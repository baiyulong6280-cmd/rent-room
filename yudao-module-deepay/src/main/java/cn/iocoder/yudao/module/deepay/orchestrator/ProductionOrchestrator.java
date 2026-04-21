package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * ProductionOrchestrator — 全链路唯一调度器，串联 12 个 Agent。
 *
 * <pre>
 * 核心原则：
 *   1. ChainAgent 最先执行 — 所有 Agent 落库时 chainCode 已存在，数据链不断裂
 *   2. Agent 只读写 Context，禁止互相调用
 *   3. Orchestrator 是唯一流程控制点
 *   4. 每步有日志 + DB 记录，全程可重放
 *
 * 执行顺序：
 *   0.  ChainAgent       → chainCode（🔥 最先，所有落库的主键）
 *   1.  TrendAgent       → referenceImages
 *   2.  DesignAgent      → designImages
 *   3.  JudgeAgent       → imageScores（落库 deepay_design_version，chainCode 已存在）
 *   4.  AIDecisionAgent  → selectedImage / decisionReason（回写 deepay_style_chain）
 *       if needRedesign  → 重跑 Design→Judge→Decision 一次
 *       if !shouldProduce → 提前返回
 *   5.  PatternAgent     → patternFile / techPackUrl（落库 deepay_production）
 *   6.  ProductAgent     → title / description（落库 deepay_product DRAFT）
 *   7.  PricingAgent     → price（更新 deepay_product.price）
 *   8.  PublishAgent     → published / productUrl（更新 deepay_product SELLING）
 *   9.  FinanceAgent     → orderId / paymentId（落库 deepay_order PENDING）
 *   10. InventoryAgent   → stock / lockedStock（落库 deepay_inventory）
 *   11. AnalyticsAgent   → analyticsReport（落库 deepay_metrics）
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    @Resource private ChainAgent      chainAgent;
    @Resource private TrendAgent      trendAgent;
    @Resource private DesignAgent     designAgent;
    @Resource private JudgeAgent      judgeAgent;
    @Resource private AIDecisionAgent aiDecisionAgent;
    @Resource private PatternAgent    patternAgent;
    @Resource private ProductAgent    productAgent;
    @Resource private PricingAgent    pricingAgent;
    @Resource private PublishAgent    publishAgent;
    @Resource private FinanceAgent    financeAgent;
    @Resource private InventoryAgent  inventoryAgent;
    @Resource private AnalyticsAgent  analyticsAgent;

    public Context run(Context ctx) {
        log.info("=== ProductionOrchestrator START keyword={} ===", ctx.keyword);

        ctx = chainAgent.run(ctx);      // 🔥 最先：生成 chainCode，作为所有落库操作的主键

        ctx = trendAgent.run(ctx);      // 感知层：爆款参考图
        ctx = designAgent.run(ctx);     // 创作层：AI 出图
        ctx = judgeAgent.run(ctx);      // 评审层：打分落库
        ctx = aiDecisionAgent.run(ctx); // 决策层：选图 + 判断

        // 门控：needRedesign → 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("[门控] needRedesign=true，重新执行设计循环，chainCode={}", ctx.chainCode);
            ctx = designAgent.run(ctx);
            ctx = judgeAgent.run(ctx);
            ctx = aiDecisionAgent.run(ctx);
        }

        // 门控：shouldProduce=false → 流程终止
        if (!Boolean.TRUE.equals(ctx.shouldProduce)) {
            log.info("[门控] shouldProduce=false，终止，reason={}", ctx.decisionReason);
            return ctx;
        }

        ctx = patternAgent.run(ctx);    // 资产层：打版文件 + tech pack
        ctx = productAgent.run(ctx);    // 商业层：商品文案落库 DRAFT
        ctx = pricingAgent.run(ctx);    // 商业层：定价写库
        ctx = publishAgent.run(ctx);    // 交付层：上架 SELLING + 访问链接

        ctx = financeAgent.run(ctx);    // 交付层：创建订单 PENDING
        ctx = inventoryAgent.run(ctx);  // 运营层：初始化库存
        ctx = analyticsAgent.run(ctx);  // 运营层：指标快照落库

        log.info("=== ProductionOrchestrator DONE chainCode={} url={} paymentId={} ===",
                ctx.chainCode, ctx.productUrl, ctx.paymentId);
        return ctx;
    }

}




