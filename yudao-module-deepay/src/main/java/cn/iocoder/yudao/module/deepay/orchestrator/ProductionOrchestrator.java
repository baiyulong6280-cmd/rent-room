package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * ProductionOrchestrator — 全链路调度，串联 12 个 Agent。
 *
 * <pre>
 * 原则：
 *   1. 所有能力可替换（每个 Agent 独立 Bean）
 *   2. 所有数据必须落库（每个 Agent 自行持久化）
 *   3. 所有流程必须可重放（每步有日志 + DB 记录）
 *   4. Agent 只读写 Context（禁止互相调用）
 *   5. Orchestrator 是唯一流程控制点
 *
 * 流程：
 *   1.  TrendAgent       → referenceImages
 *   2.  DesignAgent      → designImages
 *   3.  JudgeAgent       → imageScores（落库 deepay_design_version）
 *   4.  AIDecisionAgent  → selectedImage / needRedesign / shouldProduce（落库 deepay_style_chain）
 *       if needRedesign  → 重跑 2/3/4 一次
 *       if !shouldProduce → 提前返回
 *   5.  ChainAgent       → chainCode（落库 deepay_style_chain）
 *   6.  PatternAgent     → patternFile / techPackUrl（落库 deepay_production）
 *   7.  ProductAgent     → title / description（落库 deepay_product DRAFT）
 *   8.  PricingAgent     → price（更新 deepay_product.price）
 *   9.  PublishAgent     → published / productUrl（更新 deepay_product SELLING）
 *   10. FinanceAgent     → orderId / paymentId（落库 deepay_order PENDING）
 *   11. InventoryAgent   → stock / lockedStock（落库 deepay_inventory）
 *   12. AnalyticsAgent   → analyticsReport（落库 deepay_metrics）
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    @Resource private TrendAgent      trendAgent;
    @Resource private DesignAgent     designAgent;
    @Resource private JudgeAgent      judgeAgent;
    @Resource private AIDecisionAgent aiDecisionAgent;
    @Resource private ChainAgent      chainAgent;
    @Resource private PatternAgent    patternAgent;
    @Resource private ProductAgent    productAgent;
    @Resource private PricingAgent    pricingAgent;
    @Resource private PublishAgent    publishAgent;
    @Resource private FinanceAgent    financeAgent;
    @Resource private InventoryAgent  inventoryAgent;
    @Resource private AnalyticsAgent  analyticsAgent;

    public Context run(Context ctx) {
        log.info("=== ProductionOrchestrator START keyword={} ===", ctx.keyword);

        // 1. 感知层：找爆款参考图
        trendAgent.run(ctx);

        // 2-4. 创作层：设计 → 评分 → AI决策
        runDesignCycle(ctx);

        // 门控：needRedesign → 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("[门控] needRedesign=true，重新执行设计循环");
            runDesignCycle(ctx);
        }

        // 门控：shouldProduce=false → 流程终止（不浪费资源）
        if (!Boolean.TRUE.equals(ctx.shouldProduce)) {
            log.info("[门控] shouldProduce=false，流程终止，reason={}", ctx.decisionReason);
            return ctx;
        }

        // 5. 资产层：生成链码（唯一标识，后续所有步骤的主键）
        chainAgent.run(ctx);

        // 6. 资产层：打版（落库 deepay_production）
        patternAgent.run(ctx);

        // 7. 商业层：生成商品（落库 deepay_product DRAFT）
        productAgent.run(ctx);

        // 8. 商业层：定价（更新 deepay_product.price）
        pricingAgent.run(ctx);

        // 9. 交付层：上架（更新 deepay_product SELLING，生成访问链接）
        publishAgent.run(ctx);

        // 10. 交付层：创建收款订单（落库 deepay_order PENDING）
        financeAgent.run(ctx);

        // 11. 运营层：初始化库存（落库 deepay_inventory）
        inventoryAgent.run(ctx);

        // 12. 运营层：记录初始分析数据（落库 deepay_metrics）
        analyticsAgent.run(ctx);

        log.info("=== ProductionOrchestrator DONE chainCode={} productUrl={} paymentId={} ===",
                ctx.chainCode, ctx.productUrl, ctx.paymentId);
        return ctx;
    }

    /** 设计循环：DesignAgent → JudgeAgent → AIDecisionAgent */
    private void runDesignCycle(Context ctx) {
        designAgent.run(ctx);
        judgeAgent.run(ctx);
        aiDecisionAgent.run(ctx);
    }

}


