package com.didichuxing.datachannel.arius.admin.biz.workorder;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.common.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.bean.dto.workorder.WorkOrderProcessDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.AriusWorkOrderInfoPO;
import com.didichuxing.datachannel.arius.admin.common.bean.vo.order.AriusWorkOrderInfoVO;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUserRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.workorder.OrderStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author d06679
 * @date 2019/4/29
 */
public abstract class BaseWorkOrderHandler implements WorkOrderHandler {

    protected static final ILog    LOGGER = LogFactory.getLog(BaseWorkOrderHandler.class);

    @Autowired
    private AriusWorkOrderInfoManager ariusWorkOrderInfoManager;

    @Autowired
    private AriusUserInfoService   ariusUserInfoService;

    @Autowired
    protected OperateRecordService operateRecordService;

    /**
     * 创建一个工单
     * 1、校验用户提供数据是否合法
     * 2、提交工单
     */
    @Override
    public Result<AriusWorkOrderInfoPO> submit(WorkOrder workOrder) throws AdminOperateException {

        Result<Void> checkAuth = validateConsoleAuth(workOrder);
        if (checkAuth.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkAuth fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));

            return Result.buildFrom(checkAuth);
        }

        Result<Void> checkDuplicateOrder = validDuplicateOrder(workOrder);
        if(checkDuplicateOrder.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkDuplicateOrder fail||type={}||content={}",
                    workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));
            return Result.buildFrom(checkDuplicateOrder);
        }

        Result<Void> checkParam = validateConsoleParam(workOrder);
        if (checkParam.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkParam fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));
            return Result.buildFrom(checkParam);
        }

        workOrder.setTitle(getTitle(workOrder));

        AriusWorkOrderInfoPO ariusWorkOrderInfoPO = buildOrderPO(workOrder);
        ariusWorkOrderInfoManager.insert(ariusWorkOrderInfoPO);

        return Result.buildSuccWithTips(ariusWorkOrderInfoPO, "工单提交成功！");
    }

    /**
     * 处理工单,审核通过或者不需要审核的工单处理逻辑
     *
     * @param workOrder 工单内容
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> processAgree(WorkOrder workOrder, String approver, String opinion) throws AdminOperateException {
        Result<Void> checkParamResult = validateParam(workOrder);

        if (checkParamResult.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=processAgree||msg=checkParam fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));

            return checkParamResult;
        }

        return handleProcessAgree(workOrder, approver, opinion);
    }

    /**
     * 处理审核不同意的工单，要求配置不同意的工单回调
     *
     * @param orderPO 工单内容
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> processDisagree(AriusWorkOrderInfoPO orderPO, WorkOrderProcessDTO processDTO) {
        return doProcessDisagree(orderPO, processDTO);
    }

    /*************************************** protected method ************************************/
    /**
     * 审核不通过
     * @param orderPO
     * @return
     */
    protected Result<Void> doProcessDisagree(AriusWorkOrderInfoPO orderPO, WorkOrderProcessDTO processDTO) {
        orderPO.setApprover(processDTO.getAssignee());
        orderPO.setApproverAppId(processDTO.getAssigneeAppid());
        orderPO.setOpinion(processDTO.getComment());
        orderPO.setStatus(OrderStatusEnum.REFUSED.getCode());

        if (ariusWorkOrderInfoManager.updateOrderById(orderPO) > 0) {
            return Result.buildSucc();
        }
        return Result.buildFail("审批不通过");
    }

    /**
     * 校验申请人是否重复提交了相同类型,相同内容的工单
     * @param workOrder workorder工单
     * @return 重复性校验的结果
     */
    protected Result<Void> validDuplicateOrder(WorkOrder workOrder) {
        // 获取当前提交人已经提交待审批的工单列表
        Result<List<AriusWorkOrderInfoVO>> orderToApproveResult = ariusWorkOrderInfoManager.getOrderApplyList(workOrder.getSubmitor(), OrderStatusEnum.WAIT_DEAL.getCode());
        if (orderToApproveResult.failed()) {
            return Result.buildFrom(orderToApproveResult);
        }

        List<AriusWorkOrderInfoVO> applyListResultData = orderToApproveResult.getData();
        // 当前不存在待审批的工单时，无需做重复性校验
        if (CollectionUtils.isEmpty(applyListResultData)) {
            return Result.buildSucc();
        }

        // 遍历所有的工单，对于待审批的工单的内容和类型进行重复性的条件过滤
        for (AriusWorkOrderInfoVO param : applyListResultData) {
            if (workOrder.getType().equals(param.getType())
                    && JSON.toJSONString(workOrder.getContentObj()).equals(param.getExtensions())) {
                return Result.buildFail("重复性工单的提交");
            }
        }

        return Result.buildSucc();
    }

    /**
     * 验证用户提供的参数
     * @param workOrder 工单
     * @return result
     */
    protected abstract Result<Void> validateConsoleParam(WorkOrder workOrder);

    /**
     * 生成标题
     * @param workOrder 工单
     * @return result
     */
    protected abstract String getTitle(WorkOrder workOrder);

    /**
     * 验证用户是否有该工单权限
     * @param workOrder 工单内容
     * @return result
     */
    protected abstract Result<Void> validateConsoleAuth(WorkOrder workOrder);

    /**
     * 验证平台参数
     * @param workOrder 工单内容
     * @return result
     */
    protected abstract Result<Void> validateParam(WorkOrder workOrder);
    
    /**过程是否同意
     * 处理工单
     * @param workOrder 工单
     * @return result
     @param approver 审批人
     @throws AdminOperateException 管理操作Exception
     */
    protected abstract Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException;

    protected List<AriusUserInfo> getRDOrOPList() {
        return ariusUserInfoService
            .listByRoles(Arrays.asList(AriusUserRoleEnum.OP.getRole(), AriusUserRoleEnum.RD.getRole()));
    }

    protected List<AriusUserInfo> getOPList() {
        return ariusUserInfoService.listByRoles(Collections.singletonList(AriusUserRoleEnum.OP.getRole()));
    }

    protected boolean isRDOrOP(String userName) {
        return ariusUserInfoService.isOPByDomainAccount(userName) || ariusUserInfoService.isRDByDomainAccount(userName);
    }

    protected boolean isOP(String userName) {
        return ariusUserInfoService.isOPByDomainAccount(userName);
    }

    /*************************************** privete method ************************************/
    /**
     * 构建订单表单
     *
     * @param workOrder 工单
     * @return result
     */
    private AriusWorkOrderInfoPO buildOrderPO(WorkOrder workOrder) {
        AriusWorkOrderInfoPO orderPo = new AriusWorkOrderInfoPO();
        orderPo.setApplicant(workOrder.getSubmitor());
        orderPo.setDescription(workOrder.getDescription());
        orderPo.setExtensions(JSON.toJSONString(workOrder.getContentObj()));
        orderPo.setType(workOrder.getType());
        orderPo.setTitle(workOrder.getTitle());
        orderPo.setStatus(OrderStatusEnum.WAIT_DEAL.getCode());
        orderPo.setApplicantAppId(workOrder.getSubmitorAppid());
        return orderPo;
    }

    private Result<Void> handleProcessAgree(WorkOrder workOrder, String approver,
                                      String opinion) throws AdminOperateException {
        Result<Void> result = doProcessAgree(workOrder, approver);

        if (result.success()) {
            Result<Void> updateResult = updateWorkOrderStatus(workOrder, approver, opinion);
            if (updateResult.failed()) {
                return updateResult;
            }
        }

        return result;
    }

    private Result<Void> updateWorkOrderStatus(WorkOrder workOrder, String approver, String opinion) {
        AriusWorkOrderInfoPO orderPO = new AriusWorkOrderInfoPO();
        orderPO.setId(workOrder.getId());
        orderPO.setApprover(approver);
        orderPO.setOpinion(opinion);
        orderPO.setStatus(OrderStatusEnum.PASSED.getCode());
        Result<Void> processResult = ariusWorkOrderInfoManager.processOrder(orderPO);
        if (processResult.failed()) {
            return processResult;
        }
        return Result.buildSucc();
    }
}