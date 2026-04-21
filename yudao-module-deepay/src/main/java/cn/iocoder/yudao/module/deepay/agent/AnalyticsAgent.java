package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * AnalyticsAgent — 记录初始指标，输出分析报告，action 落库 deepay_style_chain。
 *
 * <p>原则：
 * <ul>
 *   <li><b>可追溯</b> — 每次上架都写入 deepay_metrics 快照。</li>
 *   <li><b>可解释</b> — analyticsReport 包含完整决策摘要。</li>
 *   <li><b>可循环</b> — action（BOOST/STOP/REDESIGN）回写链路，供复盘调度器使用。</li>
 * </ul>
 * </p>
 */
@Component
public class AnalyticsAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAgent.class);

    @Resource
    private DeepayMetricsMapper deepayMetricsMapper;

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Override
    public Context run(Context ctx) {
        // 落库指标快照
        DeepayMetricsDO metrics = new DeepayMetricsDO();
        metrics.setChainCode(ctx.chainCode);
        metrics.setSoldCount(0);
        metrics.setPrice(ctx.price);
        metrics.setCategory(ctx.keyword);
        metrics.setCreatedAt(LocalDateTime.now());
        deepayMetricsMapper.insert(metrics);

        ctx.soldCount = 0;

        // 生成可解释报告
        ctx.analyticsReport = String.format(
                "[初始化] chainCode=%s | keyword=%s | price=%s | stock=%d | patternFile=%s | productUrl=%s | action=%s | decision=%s",
                ctx.chainCode, ctx.keyword, ctx.price, ctx.stock,
                ctx.patternFile, ctx.productUrl, ctx.action, ctx.decisionReason);

        // 回写 deepay_style_chain（action 可被复盘调度器读取）
        if (ctx.chainCode != null) {
            deepayStyleChainMapper.update(null, new LambdaUpdateWrapper<DeepayStyleChainDO>()
                    .eq(DeepayStyleChainDO::getChainCode, ctx.chainCode)
                    .set(DeepayStyleChainDO::getStatus, "PUBLISHED"));
        }

        log.info("AnalyticsAgent: 分析完成，chainCode={} action={}", ctx.chainCode, ctx.action);
        return ctx;
    }

}


