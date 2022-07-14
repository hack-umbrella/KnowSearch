package com.didichuxing.datachannel.arius.admin.task.metadata;

import com.didiglobal.logi.job.core.consensual.ConsensualEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.MonitorJobHandler;
import com.didiglobal.logi.job.annotation.Task;
import com.didiglobal.logi.job.common.TaskResult;
import com.didiglobal.logi.job.core.job.Job;
import com.didiglobal.logi.job.core.job.JobContext;

@Task(name = "ESNodeAndIndicesMetricsCollectorBroadcastTask", description = "节点和索引指标信息采集调度任务", cron = "0 0/1 * * * ? *", autoRegister = true, consensual = ConsensualEnum.BROADCAST)
public class ESNodeAndIndicesMetricsCollectorBroadcastTask implements Job {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESNodeAndIndicesMetricsCollectorBroadcastTask.class);

    @Autowired
    private MonitorJobHandler   monitorJobHandler;

    @Override
    public TaskResult execute(JobContext jobContext) throws Exception {
        LOGGER.info("class=ESNodeAndIndicesMetricsCollectorBroadcastTask||method=execute||msg=start");
        monitorJobHandler.handleBrocastJobTask("", jobContext.getCurrentWorkerCode(), jobContext.getAllWorkerCodes());
        return TaskResult.SUCCESS;
    }
}
