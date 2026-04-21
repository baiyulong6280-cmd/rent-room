package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import cn.iocoder.yudao.module.deepay.scheduler.DeepayRetryScheduler;
import cn.iocoder.yudao.module.deepay.service.CdnService;
import cn.iocoder.yudao.module.deepay.service.ContextSnapshotService;
import cn.iocoder.yudao.module.deepay.service.DeepayAuditService;import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * ProductionOrchestrator — 全链路唯一调度器（Phase 3/4/5 升级版）。
 *
 * <pre>
 * 执行顺序：
 *   0.  ChainAgent       → chainCode
 *   1.  TrendAgent       → referenceImages（CDN 同步）
 *   2.  DesignAgent      → designImages（CDN 同步，熔断保护）
 *   3.  JudgeAgent       → imageScores
 *   4.  AIDecisionAgent  → selectedImage / needRedesign / shouldProduce（ROI 门控）
 *       [snapshot]
 *       if needRedesign  → 重跑 Design→Judge→Decision
 *       if !shouldProduce → 提前返回
 *   5.  PatternAgent     → patternFile / techPackUrl
 *   6.  ProductAgent     → title / description
 *   7.  PricingAgent     → price = cost × (1 + targetProfitRate)（Phase 4）
 *       [snapshot]
 *   8.  PublishAgent     → published / productUrl
 *   9.  FinanceAgent     → orderId / paymentId
 *   10. InventoryAgent   → stock / lockedStock
 *   11. AnalyticsAgent   → profit / roi 快照（Phase 4）
 *       [snapshot]
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    @Resource private ChainAgent           chainAgent;
    @Resource private TrendAgent           trendAgent;
    @Resource private DesignAgent          designAgent;
    @Resource private JudgeAgent           judgeAgent;
    @Resource private AIDecisionAgent      aiDecisionAgent;
    @Resource private PatternAgent         patternAgent;
    @Resource private ProductAgent         productAgent;
    @Resource private PricingAgent         pricingAgent;
    @Resource private PublishAgent         publishAgent;
    @Resource private FinanceAgent         financeAgent;
    @Resource private InventoryAgent       inventoryAgent;
    @Resource private AnalyticsAgent       analyticsAgent;

    // Phase 5 agents
    @Resource private DemandAgent          demandAgent;
    @Resource private ClientAgent          clientAgent;
    @Resource private ProductionPlanner    productionPlanner;

    @Resource private ContextSnapshotService snapshotService;
    @Resource private DeepayAuditService     auditService;
    @Resource private CdnService             cdnService;
    @Resource private DeepayRetryScheduler   retryScheduler;

    public Context run(Context ctx) {
        log.info("=== ProductionOrchestrator START keyword={} ===", ctx.keyword);

        ctx = chainAgent.run(ctx);

        ctx = trendAgent.run(ctx);
        ctx = syncReferenceImagesToCdn(ctx);

        // DesignAgent（受熔断器保护，失败时已降级为默认图）
        try {
            ctx = designAgent.run(ctx);
            ctx = syncDesignImagesToCdn(ctx);
        } catch (Exception e) {
            log.error("[Orchestrator] DesignAgent 失败，登记重试 chainCode={}", ctx.chainCode, e);
            retryScheduler.register(ctx.chainCode, "AI_DESIGN", e.getMessage());
            return ctx;
        }

        ctx = judgeAgent.run(ctx);
        ctx = aiDecisionAgent.run(ctx);
        snapshotService.save(ctx, "AIDecisionAgent");
        auditService.log(ctx.chainCode, "CREATE",
                "keyword=" + ctx.keyword, "action=" + ctx.action + " shouldProduce=" + ctx.shouldProduce);

        // 门控：needRedesign → 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("[门控] needRedesign=true，重新执行设计循环，chainCode={}", ctx.chainCode);
            try {
                ctx = designAgent.run(ctx);
                ctx = syncDesignImagesToCdn(ctx);
            } catch (Exception e) {
                log.error("[Orchestrator] 重设计 DesignAgent 失败 chainCode={}", ctx.chainCode, e);
                retryScheduler.register(ctx.chainCode, "AI_DESIGN", e.getMessage());
                return ctx;
            }
            ctx = judgeAgent.run(ctx);
            ctx = aiDecisionAgent.run(ctx);
            snapshotService.save(ctx, "AIDecisionAgent[redesign]");
        }

        // 门控：shouldProduce=false → 流程终止
        if (!Boolean.TRUE.equals(ctx.shouldProduce)) {
            log.info("[门控] shouldProduce=false，终止，reason={}", ctx.decisionReason);
            return ctx;
        }

        try {
            ctx = patternAgent.run(ctx);
        } catch (Exception e) {
            log.error("[Orchestrator] PatternAgent 失败 chainCode={}", ctx.chainCode, e);
            retryScheduler.register(ctx.chainCode, "PATTERN", e.getMessage());
            return ctx;
        }

        ctx = productAgent.run(ctx);
        ctx = pricingAgent.run(ctx);
        snapshotService.save(ctx, "PricingAgent");
        auditService.log(ctx.chainCode, "REPRICE",
                "cost=" + ctx.costPrice, "price=" + ctx.price);

        ctx = publishAgent.run(ctx);
        auditService.log(ctx.chainCode, "PUBLISH",
                "status=DRAFT", "status=SELLING url=" + ctx.productUrl);

        ctx = financeAgent.run(ctx);
        ctx = inventoryAgent.run(ctx);
        ctx = analyticsAgent.run(ctx);
        snapshotService.save(ctx, "AnalyticsAgent");

        // Phase 5: 需求预测 → 生产计划 → 客户分层定价
        ctx = demandAgent.run(ctx);
        ctx = productionPlanner.run(ctx);
        if (ctx.clientId != null) {
            ctx = clientAgent.run(ctx);
        }
        snapshotService.save(ctx, "Phase5[Demand+Production+Client]");

        log.info("=== ProductionOrchestrator DONE chainCode={} url={} price={} profit={} roi={} predicted={} suggestedProd={} ===",
                ctx.chainCode, ctx.productUrl, ctx.price, ctx.profit, ctx.roi,
                ctx.predictedDemand, ctx.suggestedProductionQty);
        return ctx;
    }

    // ---------------------------------------------------------------- CDN helpers

    private Context syncReferenceImagesToCdn(Context ctx) {
        if (ctx.referenceImages != null && !ctx.referenceImages.isEmpty()) {
            List<String> cdnImages = cdnService.syncAllToCdn(ctx.referenceImages);
            ctx.referenceImages = cdnImages;
        }
        return ctx;
    }

    private Context syncDesignImagesToCdn(Context ctx) {
        if (ctx.designImages != null && !ctx.designImages.isEmpty()) {
            List<String> cdnImages = cdnService.syncAllToCdn(ctx.designImages);
            ctx.designImages = cdnImages;
        }
        if (ctx.selectedImage != null) {
            ctx.selectedImage = cdnService.syncToCdn(ctx.selectedImage);
        }
        return ctx;
    }

}
