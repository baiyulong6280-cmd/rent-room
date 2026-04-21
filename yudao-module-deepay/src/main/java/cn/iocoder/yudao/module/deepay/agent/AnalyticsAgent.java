package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * AnalyticsAgent — 记录销售初始数据，写入 deepay_metrics。
 */
@Component
public class AnalyticsAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAgent.class);

    @Resource
    private DeepayMetricsMapper deepayMetricsMapper;

    @Override
    public Context run(Context ctx) {
        DeepayMetricsDO metrics = new DeepayMetricsDO();
        metrics.setChainCode(ctx.chainCode);
        metrics.setSoldCount(0);
        metrics.setPrice(ctx.price);
        metrics.setCategory(ctx.keyword);
        metrics.setCreatedAt(LocalDateTime.now());
        deepayMetricsMapper.insert(metrics);

        ctx.soldCount = 0;
        ctx.analyticsReport = "chainCode=" + ctx.chainCode + " price=" + ctx.price + " stock=" + ctx.stock;
        log.info("AnalyticsAgent: metrics 记录完成，chainCode={}", ctx.chainCode);
        return ctx;
    }

}

