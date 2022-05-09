package com.didichuxing.datachannel.arius.admin.biz.worktask.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.AriusWorkOrderInfoManager;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.PhyClusterPluginOperationContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.utils.WorkOrderTaskConverter;
import com.didichuxing.datachannel.arius.admin.biz.worktask.AriusOpTaskManager;
import com.didichuxing.datachannel.arius.admin.biz.worktask.WorkTaskHandler;
import com.didichuxing.datachannel.arius.admin.biz.worktask.ecm.EcmTaskManager;
import com.didichuxing.datachannel.arius.admin.common.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.bean.common.ecm.EcmParamBase;
import com.didichuxing.datachannel.arius.admin.common.bean.common.ecm.EsConfigAction;
import com.didichuxing.datachannel.arius.admin.common.bean.common.ecm.elasticcloud.ElasticCloudCommonActionParam;
import com.didichuxing.datachannel.arius.admin.common.bean.common.ecm.host.HostsParamBase;
import com.didichuxing.datachannel.arius.admin.common.bean.dto.task.AriusOpTaskProcessDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.dto.task.ecm.EcmTaskDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.esconfig.ESConfig;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.task.AriusOpTask;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.ecm.EcmTask;
import com.didichuxing.datachannel.arius.admin.common.bean.po.task.ecm.EcmTaskPO;
import com.didichuxing.datachannel.arius.admin.common.bean.vo.order.detail.OrderDetailBaseVO;
import com.didichuxing.datachannel.arius.admin.common.constant.ecm.EcmTaskStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.ecm.EcmTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.esconfig.EsConfigActionEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.resource.ESClusterTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.task.WorkTaskStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.workorder.OperationTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.event.ecm.EcmTaskEditEvent;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESClusterConfigService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPluginService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

/**
 * ecm工作任务处理程序
 *
 * @author
 * @date 2022/05/09
 */
@Service("ecmWorkTaskHandler")
public class ECMWorkTaskHandler implements WorkTaskHandler, ApplicationListener<EcmTaskEditEvent> {

    private static final ILog      LOGGER = LogFactory.getLog(ECMWorkTaskHandler.class);

    @Autowired
    private EcmTaskManager         ecmTaskManager;

    @Autowired
    private AriusOpTaskManager ariusOpTaskManager;

    @Autowired
    private ESClusterService       esClusterService;

    @Autowired
    private ESClusterConfigService esClusterConfigService;

    @Autowired
    private AriusWorkOrderInfoManager ariusWorkOrderInfoManager;

    @Autowired
    private ESPluginService        esPluginService;

    @Autowired
    private ClusterPhyService      esClusterPhyService;

    @Override
    public Result<AriusOpTask> addTask(AriusOpTask ariusOpTask) {
        if (AriusObjUtils.isNull(ariusOpTask.getExpandData())) {
            return Result.buildParamIllegal("提交内容为空");
        }

        EcmTaskDTO ecmTaskDTO = ConvertUtil.str2ObjByJson(ariusOpTask.getExpandData(), EcmTaskDTO.class);
        Result<Long> ret = ecmTaskManager.saveEcmTask(ecmTaskDTO);
        if (null == ret || ret.failed()) {
            return Result.buildFail("生成集群新建操作任务失败!");
        }

        ariusOpTask.setBusinessKey(String.valueOf(ret.getData()));
        ariusOpTask.setTitle(ecmTaskDTO.getTitle());
        ariusOpTask.setCreateTime(new Date());
        ariusOpTask.setUpdateTime(new Date());
        ariusOpTask.setStatus(WorkTaskStatusEnum.WAITING.getStatus());
        ariusOpTask.setDeleteFlag(false);
        ariusOpTaskManager.insert(ariusOpTask);

        return Result.buildSucc(ariusOpTask);
    }

    @Override
    public boolean existUnClosedTask(String key, Integer type) {
        return ecmTaskManager.existUnClosedEcmTask(Long.valueOf(key));
    }

    @Override
    public Result<Void> process(AriusOpTask ariusOpTask, Integer step, String status, String expandData) {
        if (AriusObjUtils.isNull(ariusOpTask.getExpandData())) {
            return Result.buildParamIllegal("提交内容为空");
        }

        EcmTaskPO ecmTaskPO = JSON.parseObject(ariusOpTask.getExpandData(), EcmTaskPO.class);

        ariusOpTask.setStatus(status);
        ariusOpTask.setUpdateTime(new Date());
        ariusOpTask.setExpandData(JSON.toJSONString(ecmTaskPO));
        ariusOpTaskManager.updateTask(ariusOpTask);

        return Result.buildSucc();
    }

 

    @Override
    public void onApplicationEvent(EcmTaskEditEvent event) {
        EcmTask ecmTask = event.getEditTask();

        if (null == ecmTask) {
            return;
        }

        handlerRestartPostConfig(ecmTask);
        handlerRestartPostPlugin(ecmTask);

        Result<AriusOpTask> result = ariusOpTaskManager.getLatestTask(String.valueOf(ecmTask.getId()), ecmTask.getOrderType());
        if (result.failed()) {
            return;
        }
        AriusOpTaskProcessDTO processDTO = new AriusOpTaskProcessDTO();
        processDTO.setStatus(ecmTask.getStatus());
        processDTO.setTaskId(result.getData().getId());
        processDTO.setExpandData(JSON.toJSONString(ecmTask));
        ariusOpTaskManager.processTask(processDTO);

        LOGGER.info("class=ECMWorkTaskHandler||method=onApplicationEvent||ecmTaskId={}||event=EcmEditTaskEvent", ecmTask.getId());
    }

    /**************************************** private methods ****************************************/
    /**
     * Ecm重启操作后处理Es集群配置相关信息
     * @param ecmTask
     */
    private void handlerRestartPostConfig(EcmTask ecmTask) {
        //1.判断是不是重启类型的工单
        if (EcmTaskTypeEnum.RESTART.getCode() != ecmTask.getOrderType()) {
            return;
        }
        //2.判断重启类型是否成功
        if (!EcmTaskStatusEnum.SUCCESS.getValue().equals(ecmTask.getStatus())) {
            return;
        }

        //3.判断是不是配置重启类型的工单, configIds为空则为非配置重启
        List<Long> actionEsConfigIds = Lists.newArrayList();
        Integer actionType = Integer.MIN_VALUE;
        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);

        if (ESClusterTypeEnum.ES_HOST.getCode() == ecmTask.getType()) {
            List<HostsParamBase> hostsParamBases = ConvertUtil.list2List(ecmParamBaseList, HostsParamBase.class);
            for(HostsParamBase hostsParamBase:hostsParamBases) {
                if(null == hostsParamBase.getEsConfigAction()) {
                    return;
                }
            }
            actionType = hostsParamBases.stream().map(HostsParamBase::getEsConfigAction)
                .map(EsConfigAction::getActionType).findAny().orElse(null);

            hostsParamBases.stream()
                .filter(r -> !AriusObjUtils.isNull(r) && !AriusObjUtils.isNull(r.getEsConfigAction())
                             && CollectionUtils.isNotEmpty(r.getEsConfigAction().getActionEsConfigIds()))
                .forEach(param -> actionEsConfigIds.addAll(param.getEsConfigAction().getActionEsConfigIds()));

        } else if (ESClusterTypeEnum.ES_DOCKER.getCode() == ecmTask.getType()) {
            List<ElasticCloudCommonActionParam> cloudCommonActionParams = ConvertUtil.list2List(ecmParamBaseList,
                ElasticCloudCommonActionParam.class);
            actionType = cloudCommonActionParams.stream().map(ElasticCloudCommonActionParam::getEsConfigActions)
                .map(EsConfigAction::getActionType).findAny().orElse(null);

            cloudCommonActionParams.stream()
                .filter(r -> !AriusObjUtils.isNull(r) && !AriusObjUtils.isNull(r.getEsConfigActions())
                             && CollectionUtils.isNotEmpty(r.getEsConfigActions().getActionEsConfigIds()))
                .forEach(param -> actionEsConfigIds.addAll(param.getEsConfigActions().getActionEsConfigIds()));
        } else {
            LOGGER.error(
                "class=ECMWorkTaskHandler||method=handlerRestartConfig||ecmTaskId={}||msg=Type does not exist, require docker or host",
                ecmTask.getId());
        }

        if (CollectionUtils.isEmpty(actionEsConfigIds)) {
            return;
        }

        try {
            //4.任务成功进行配置回写处理
            handleSuccessEcmConfigRestartTask(actionType, actionEsConfigIds, ecmTask);
        } catch (Exception e) {
            LOGGER.error("class=ECMWorkTaskHandler||method=handlerRestartConfig||ecmTaskId={}||msg={}", ecmTask.getId(),
                e.getStackTrace());
        }

    }

    private void handleSuccessEcmConfigRestartTask(Integer actionType, List<Long> actionEsConfigIds, EcmTask ecmTask) {
        for (Long actionEsConfigId : actionEsConfigIds) {
            ESConfig esConfig = esClusterConfigService.getEsConfigById(actionEsConfigId);
            if (AriusObjUtils.isNull(esConfig)) {
                LOGGER.error(
                        "class=ECMWorkTaskHandler||method=handlerRestartConfig||ecmTaskId={}||clusterId={}||msg=es config does not exist",
                        ecmTask.getId(), ecmTask.getPhysicClusterId());
                return;
            }
            //删除操作, 删除当前集群角色配置类型下的所有信息
            if (EsConfigActionEnum.DELETE.getCode() == actionType) {
                ESConfig esConfigById = esClusterConfigService.getEsConfigById(actionEsConfigId);
                Result<Void> result = esClusterConfigService.deleteByClusterIdAndTypeAndEngin(esConfigById.getClusterId(), esConfigById.getTypeName(), esConfigById.getEnginName());
                if (result.failed()) {
                    LOGGER.error(
                            "class=ECMWorkTaskHandler||method=handlerRestartConfig||ecmTaskId={}||clusterId={}||msg=fail to set new config valid",
                            ecmTask.getId(), ecmTask.getPhysicClusterId());
                }
                return;
            }

            //编辑操作, 设置当前版本为有效, 原版本为无效
            Result<Void> result = esClusterConfigService.setConfigValid(actionEsConfigId);
            if (result.failed()) {
                LOGGER.error(
                        "class=ECMWorkTaskHandler||method=handlerRestartConfig||ecmTaskId={}||clusterId={}||msg=fail to set edit config valid",
                        ecmTask.getId(), ecmTask.getPhysicClusterId());
            }

            if (EsConfigActionEnum.EDIT.getCode() == actionType) {
                esClusterConfigService.setOldConfigInvalid(esConfig);
            }
        }
    }

    /**
     * Ecm重启操作后处理Es集群插件相关信息
     * @param ecmTask 任务
     */
    private void handlerRestartPostPlugin(EcmTask ecmTask) {
        // 1.判断是不是重启类型的工单
        if (EcmTaskTypeEnum.RESTART.getCode() != ecmTask.getOrderType()) {
            return;
        }
        // 2.判断重启类型是否成功
        if (!EcmTaskStatusEnum.SUCCESS.getValue().equals(ecmTask.getStatus())) {
            return;
        }

        // 3.判断当前重启操作是否是插件安装或者卸载
        List<EcmParamBase> ecmParamBases = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);
        if(CollectionUtils.isEmpty(ecmParamBases)) {
            return;
        }
        HostsParamBase hostsParamBase = (HostsParamBase) ecmParamBases.get(0);
        if(AriusObjUtils.isNull(hostsParamBase.getEsPluginAction())) {
           return;
        }

        try {
            // 4.任务成功进行插件回写处理
            handleSuccessEcmPluginRestartTask(ecmTask);
        } catch (Exception e) {
            LOGGER.error(
                    "class=ECMWorkTaskHandler||method=handlerRestartPlugin||ecmTaskId={}||msg={}",
                    ecmTask.getId(),
                    e.getStackTrace());
        }
    }

    /**
     * 当因插件操作任务而重启集群成功后，进行一些插件信息的回写
     *
     * @param ecmTask 任务
     */
    private void handleSuccessEcmPluginRestartTask(EcmTask ecmTask) {
        OrderDetailBaseVO orderDetailBaseVO = ariusWorkOrderInfoManager.getById(ecmTask.getWorkOrderId()).getData();
        PhyClusterPluginOperationContent content = JSON.parseObject(orderDetailBaseVO.getDetail(), PhyClusterPluginOperationContent.class);

        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(ecmTask.getPhysicClusterId().intValue());
        List<Long> plugIdList = ListUtils.string2LongList(clusterPhy.getPlugIds());

        String cluster = esClusterPhyService.getClusterById(ecmTask.getPhysicClusterId().intValue()).getCluster();
        Map</*节点名称*/String, /*插件名称列表*/List<String>> node2PluginMap = esClusterService.syncGetNode2PluginsMap(cluster);
        if (null == node2PluginMap) {
            LOGGER.warn("class=ECMWorkTaskHandler||method=handleSuccessEcmPluginRestartTask||cluster={}||errMsg={node2PluginMap is null}", cluster);
            return;
        }
        String pluginName = esPluginService.getESPluginById(content.getPluginId()).getName();

        // 记录插件安装或者卸载失败的集群节点名称
        List<String> failPluginOperationNodeNames = Lists.newArrayList();
        if (OperationTypeEnum.INSTALL.getCode().equals(content.getOperationType())) {
            for (Map.Entry<String, List<String>> entry : node2PluginMap.entrySet()) {
                if (!entry.getValue().contains(pluginName)) {
                    failPluginOperationNodeNames.add(entry.getKey());
                }
            }

            if (!failPluginOperationNodeNames.isEmpty()) {
                LOGGER.warn("class=ECMWorkTaskHandler||method=handleSuccessEcmPluginRestartTask||msg=节点列表{}插件{}安装失败", failPluginOperationNodeNames, pluginName);
                ecmTask.setStatus(EcmTaskStatusEnum.FAILED.getValue());
                return;
            }

            // 将插件信息同步到物理集群中
            plugIdList.add(content.getPluginId());
        } else if (OperationTypeEnum.UNINSTALL.getCode().equals(content.getOperationType())) {
            for (Map.Entry<String, List<String>> entry : node2PluginMap.entrySet()) {
                if (entry.getValue().contains(pluginName)) {
                    failPluginOperationNodeNames.add(entry.getKey());
                }
            }

            if (!failPluginOperationNodeNames.isEmpty()) {
                LOGGER.warn("class=ECMWorkTaskHandler||method=handleSuccessEcmPluginRestartTask||msg=节点列表{}插件{}卸载失败", failPluginOperationNodeNames, pluginName);
                ecmTask.setStatus(EcmTaskStatusEnum.FAILED.getValue());
                return;
            }

            // 将插件信息同步到物理集群中
            plugIdList.remove(content.getPluginId());
        }
        esClusterPhyService.updatePluginIdsById(ListUtils.longList2String(plugIdList.stream().distinct().collect(Collectors.toList())), clusterPhy.getId());
    }
}