package com.didiglobal.logi.op.manager.infrastructure.deployment.zeus;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.didiglobal.logi.op.manager.infrastructure.deployment.ZeusSubTaskLog;
import com.didiglobal.logi.op.manager.infrastructure.exception.ZeusOperationException;
import com.didiglobal.logi.op.manager.infrastructure.util.HttpUtil;
import com.google.common.collect.Maps;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author didi
 * @date 2022-07-08 7:00 下午
 */
@Data
@Component
public class ZeusServiceImpl implements ZeusService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ZeusServiceImpl.class);

    @Value("${zeus.server}")
    private String zeusServer;

    @Value("${zeus.token}")
    private String zeusToken;

    @Value("${zeus.grpId}")
    private String zeusGrpId;

    @Value("${zeus.user:root}")
    private String zeusUser;

    @Value("${zeus.batch:1}")
    private Integer zeusBatch;

    @Value("${zeus.timeOut}")
    private Integer zeusTimeOut;

    @Value("${zeus.tolerance:0}")
    private Integer zeusTolerance;

    private static final String API_TASK = "/api/task/";

    private static final String API_TEMPLATE = "http://%s/api/grp/%s/tpl/new?token=%s";

    private static final String API_EDIT_TEMPLATE = "http://%s/api/tpl/%s/edit?token=%s";

    private static final String API_EXECUTE_TASK = "http://%s/api/task?token=%s";

    private static final String API_TASK_STATUS = "http://%s/api/task/%s/result";

    private static final String API_TEMPLATE_REMOVE = "http://%s/api/tpl/%s?token=%s";

    private static final String API_TASK_ACTION = "http://%s/api/task/action?token=%s";

    private static final String API_HOST_ACTION = "http://%s/api/task/host-action?token=%s";

    private static final String EMPTY_STRING = "";

    @Override
    public String createTemplate(ZeusTemplate zeusTemplate) throws ZeusOperationException {
        zeusTemplate.setBatch(zeusBatch);
        zeusTemplate.setAccount(zeusUser);
        zeusTemplate.setTolerance(zeusTolerance);
        String url = String.format(API_TEMPLATE, zeusServer, zeusGrpId, zeusToken);
        ZeusResult result = getZeusResultForPost(zeusTemplate, url);
        return result.getData().toString();
    }

    @Override
    public void editTemplate(ZeusTemplate zeusTemplate) throws ZeusOperationException {
        String url = String.format(API_EDIT_TEMPLATE, zeusServer, zeusTemplate.getId(), zeusToken);
        ZeusResult result = getZeusResultForPost(zeusTemplate, url);
        LOGGER.info("edit Template[{}], message:{}", zeusTemplate.getId(), result.getMsg());
    }

    @Override
    public Integer executeTask(ZeusTask zeusTask) throws ZeusOperationException {
        String url = String.format(API_EXECUTE_TASK, zeusServer, zeusToken);
        ZeusResult result = getZeusResultForPost(zeusTask, url);
        return Integer.parseInt(result.getData().toString());
    }

    @Override
    public ZeusTaskStatus getTaskStatus(int taskId) throws ZeusOperationException {
        try {
            ZeusResult result = HttpUtil.getRestTemplate().getForObject(String.format(API_TASK_STATUS, zeusServer, taskId), ZeusResult.class);
            if (result.failed()) {
                throw new ZeusOperationException(result.getMsg());
            }
            return JSON.parseObject(JSON.toJSONString(result.getData()), ZeusTaskStatus.class);
        } catch (ZeusOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    @Override
    public String getTaskStdOutLog(int taskId,String hostname) throws ZeusOperationException {
        try {
            ZeusTaskStatus zeusTaskStatus = getTaskStatus(1);
            String s1 = zeusTaskStatus.toString();
            String url;
            if (null != hostname) {
                url = zeusServer + API_TASK + taskId + "/stdouts.json?hostname=" + hostname;
            } else {
                url = zeusServer +API_TASK + taskId +"/stdouts.json";
            }
            ZeusResult result = getZeusResultForGet(buildHeader(),url);
            if (result.failed()) {
                throw new ZeusOperationException(result.getMsg());
            }
            List<ZeusSubTaskLog> zeusSubTaskLogs = JSON.parseArray(JSON.toJSONString(result.getData()),ZeusSubTaskLog.class);
            if (zeusSubTaskLogs == null || zeusSubTaskLogs.isEmpty()) {
                return EMPTY_STRING;
            }
            return zeusSubTaskLogs.get(0).getStdout();
        } catch (ZeusOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    @Override
    public String getTaskStdErrLog(int taskId,String hostname) throws ZeusOperationException {
        try {
            String url;
            if (null != hostname) {
                url = zeusServer + API_TASK + taskId + "/stderrs.json?hostname=" + hostname;
            } else {
                url = zeusServer +API_TASK + taskId +"/stderrs.json";
            }
            ZeusResult result = getZeusResultForGet(buildHeader(),url);
            if (result.failed()) {
                throw new ZeusOperationException(result.getMsg());
            }
            List<ZeusSubTaskLog> zeusSubTaskLogs = JSON.parseArray(JSON.toJSONString(result.getData()),ZeusSubTaskLog.class);
            if (zeusSubTaskLogs == null || zeusSubTaskLogs.isEmpty()) {
                return EMPTY_STRING;
            }
            return zeusSubTaskLogs.get(0).getStderr();
        } catch (ZeusOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    @Override
    public void deleteTemplate(int templateId) throws ZeusOperationException {
        try {
            String url = String.format(API_TEMPLATE_REMOVE, zeusServer, templateId, zeusToken);
            HttpUtil.getRestTemplate().delete(url);
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    @Override
    public void actionTask(JSONObject param) throws ZeusOperationException {
        String url = String.format(API_TASK_ACTION, zeusServer, zeusToken);
        getZeusResultForPost(param, url);
    }

    @Override
    public void actionHost(JSONObject param) throws ZeusOperationException {
        String url = String.format(API_HOST_ACTION, zeusServer, zeusToken);
        getZeusResultForPost(param, url);
    }


    @NotNull
    private ZeusResult getZeusResultForPost(Object param, String url) throws ZeusOperationException {
        try {
            ZeusResult result = HttpUtil.getRestTemplate().postForObject(url, JSON.toJSONString(param), ZeusResult.class);
            if (result.failed()) {
                throw new ZeusOperationException(result.getMsg());
            }
            return result;
        } catch (ZeusOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    @NotNull
    private ZeusResult getZeusResultForGet(Map<String, String> headers, String url) throws ZeusOperationException {
        try {
            ZeusResult result = HttpUtil.getRestTemplate().getForObject(url, ZeusResult.class);
            if (result.failed()) {
                throw new ZeusOperationException(result.getMsg());
            }
            return result;
        } catch (ZeusOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new ZeusOperationException(e);
        }
    }

    private Map<String, String> buildHeader() {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
