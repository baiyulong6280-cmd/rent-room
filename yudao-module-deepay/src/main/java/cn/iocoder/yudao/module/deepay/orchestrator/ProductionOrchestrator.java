package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * ProductionOrchestrator — 总调度，串联所有 Agent。
 *
 * <pre>
 * 1. TrendAgent
 * 2. DesignAgent
 * 3. JudgeAgent
 * 4. AIDecisionAgent
 *    if needRedesign → 重跑 2/3/4 一次
 *    if !shouldProduce → 提前返回
 * 5. ChainAgent
 * 6. PatternAgent
 * 7. ProductAgent
 * 8. PricingAgent
 * 9. PublishAgent
 * 10. InventoryAgent
 * 11. AnalyticsAgent
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    @Resource private TrendAgent     trendAgent;
    @Resource private DesignAgent    designAgent;
    @Resource private ChainAgent     chainAgent;
    @Resource private ProductAgent   productAgent;
    @Resource private PublishAgent   publishAgent;
    @Resource private InventoryAgent inventoryAgent;
    @Resource private AnalyticsAgent analyticsAgent;

    public Context run(Context ctx) {
        log.info("ProductionOrchestrator 启动，keyword={}", ctx.keyword);

        // 1. 找爆款
        trendAgent.run(ctx);

        // 2-4. 设计 → 评分 → 决策
        runDesignCycle(ctx);

        // needRedesign: 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("needRedesign=true，重新执行设计循环");
            runDesignCycle(ctx);
        }

        // shouldProduce 为 false：流程终止
        if (!Boolean.TRUE.equals(ctx.shouldProduce)) {
            log.info("shouldProduce=false，流程终止，不进入生产");
            return ctx;
        }

        // 5. 生成链码
        chainAgent.run(ctx);

        // 6-9. 打版 → 商品 → 定价 → 上架
        new PatternAgent().run(ctx);
        productAgent.run(ctx);
        new PricingAgent().run(ctx);
        publishAgent.run(ctx);

        // 10-11. 库存 → 数据
        inventoryAgent.run(ctx);
        analyticsAgent.run(ctx);

        log.info("ProductionOrchestrator 完成，chainCode={}", ctx.chainCode);
        return ctx;
    }

    /** DesignAgent → JudgeAgent → AIDecisionAgent */
    private void runDesignCycle(Context ctx) {
        designAgent.run(ctx);
        new JudgeAgent().run(ctx);
        new AIDecisionAgent().run(ctx);
    }

}

