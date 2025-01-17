package com.didichuxing.datachannel.arius.admin.common.bean.vo.metrics.other.cluster;

import com.didichuxing.datachannel.arius.admin.common.bean.vo.metrics.percentiles.ESPercentileMetricsVO;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by linyunan on 2021-08-01
 */
@Data
@NoArgsConstructor
@ApiModel("查询延时")
public class SearchLatencyMetricsVO extends ESPercentileMetricsVO {
}
