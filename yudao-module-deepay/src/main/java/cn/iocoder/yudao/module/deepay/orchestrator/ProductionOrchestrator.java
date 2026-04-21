package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import cn.iocoder.yudao.module.deepay.scheduler.DeepayRetryScheduler;
import cn.iocoder.yudao.module.deepay.service.CdnService;
import cn.iocoder.yudao.module.deepay.service.ContextSnapshotService;
import cn.iocoder.yudao.module.deepay.service.DeepayAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * ProductionOrchestrator — 全链路唯一调度器（Phase 6 最终版）。
 *
 * <pre>
 * ===== Phase 6 完整流程 =====
 *
 * 1. CustomerProfileAgent  — 加载客户画像（category/style/market/…）
 *    if (confidence &lt; 0.6) → SmartQuestionAgent（问 4 道题补填）
 *    CustomerProfileAgent.updateProfile — 回写问卷答案到画像
 *
 * 2. TrendSourceAgent      — 内部热销趋势（trendImages + trendKeywords）
 *    CategoryFilterAgent   — 丢弃非本品类趋势图
 *
 * 3. ChainAgent            — 生成 chainCode
 *
 * 4. DesignAgent           — 个性化 prompt：category+style+market+age+gender+trend
 *    [CDN sync]
 *    CategoryFilterAgent   — 丢弃非本品类设计图（防错核心）
 *    JudgeAgent            — 历史加权打分
 *    AIDecisionAgent       — 品类+风格过滤后选最高分（ROI 门控）
 *    [snapshot]
 *
 *    if needRedesign → 重跑一次 Design→Filter→Judge→Decision
 *    if !shouldProduce → 终止
 *
 * 5. PatternAgent / ProductAgent / PricingAgent / PublishAgent
 *    FinanceAgent / InventoryAgent / AnalyticsAgent
 *    [snapshot]
 *
 * 6. Phase 5：DemandAgent / ProductionPlanner / ClientAgent
 *
 * 7. PreferenceLearningAgent — 自动记录本次选图偏好（+confidence）
 *    CustomerProfileAgent.updateProfile — 最终回写画像
 *    [snapshot]
 *
 * 验收：
 *   ✔ 不输入关键词也能跑（画像填充 keyword）
 *   ✔ 不会出错类目（CategoryFilterAgent 双重防护）
 *   ✔ 客户选一次 → 下次更准（PreferenceLearningAgent 累积）
 *   ✔ 第二次基本不用问（confidence 累积超 0.6 后跳过问卷）
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    // ---- Core agents ----
    @Resource private ChainAgent              chainAgent;
    @Resource private TrendAgent              trendAgent;
    @Resource private DesignAgent             designAgent;
    @Resource private JudgeAgent              judgeAgent;
    @Resource private AIDecisionAgent         aiDecisionAgent;
    @Resource private PatternAgent            patternAgent;
    @Resource private ProductAgent            productAgent;
    @Resource private PricingAgent            pricingAgent;
    @Resource private PublishAgent            publishAgent;
    @Resource private FinanceAgent            financeAgent;
    @Resource private InventoryAgent          inventoryAgent;
    @Resource private AnalyticsAgent          analyticsAgent;

    // ---- Phase 5 agents ----
    @Resource private DemandAgent             demandAgent;
    @Resource private ClientAgent             clientAgent;
    @Resource private ProductionPlanner       productionPlanner;

    // ---- Phase 6 agents ----
    @Resource private CustomerProfileAgent    customerProfileAgent;
    @Resource private SmartQuestionAgent      smartQuestionAgent;
    @Resource private TrendSourceAgent        trendSourceAgent;
    @Resource private CategoryFilterAgent     categoryFilterAgent;
    @Resource private PreferenceLearningAgent preferenceLearningAgent;

    // ---- Services ----
    @Resource private ContextSnapshotService snapshotService;
    @Resource private DeepayAuditService     auditService;
    @Resource private CdnService             cdnService;
    @Resource private DeepayRetryScheduler   retryScheduler;

    // ====================================================================
    // 主入口
    // ====================================================================

    public Context run(Context ctx) {
        log.info("=== ProductionOrchestrator START keyword={} customerId={} ===",
                ctx.keyword, ctx.customerId);

        // -------- Phase 6 / Step 1: 客户画像加载 + 问卷补填 --------
        ctx = customerProfileAgent.run(ctx);  // loadProfile 填充 category/style/market…
        if (SmartQuestionAgent.needsQuestionnaire(ctx)) {
            ctx = smartQuestionAgent.run(ctx);
            // 回写问卷答案到画像（让下次跳过问卷）
            if (ctx.customerId != null) {
                customerProfileAgent.updateProfile(ctx.customerId, ctx);
            }
        }

        // -------- Phase 6 / Step 2: 内部趋势 + 品类过滤（趋势图）--------
        ctx = trendSourceAgent.run(ctx);          // 内部热销趋势（不接外部 API）
        // 将 referenceImages 同步为趋势图（供 TrendAgent 的 CDN 逻辑兜底）
        if (ctx.trendImages != null && !ctx.trendImages.isEmpty()) {
            ctx.referenceImages = ctx.trendImages;
        } else {
            ctx = trendAgent.run(ctx);            // fallback：原始 TrendService
        }
        ctx = syncReferenceImagesToCdn(ctx);

        // -------- Phase 3/4/5 / Step 3: 链路生成 --------
        ctx = chainAgent.run(ctx);

        // -------- Phase 6 / Step 4: 设计 + 双重品类过滤 + 评审 --------
        try {
            ctx = designAgent.run(ctx);            // 个性化 prompt
            ctx = syncDesignImagesToCdn(ctx);
            ctx = categoryFilterAgent.run(ctx);    // 第一道防线：过滤非本品类设计图
        } catch (Exception e) {
            log.error("[Orchestrator] DesignAgent 失败，登记重试 chainCode={}", ctx.chainCode, e);
            retryScheduler.register(ctx.chainCode, "AI_DESIGN", e.getMessage());
            return ctx;
        }

        ctx = judgeAgent.run(ctx);
        ctx = aiDecisionAgent.run(ctx);            // 内部再次按品类+风格过滤后选最高分
        snapshotService.save(ctx, "AIDecisionAgent");
        auditService.log(ctx.chainCode, "CREATE",
                "keyword=" + ctx.keyword + " category=" + ctx.category,
                "action=" + ctx.action + " shouldProduce=" + ctx.shouldProduce);

        // 门控：needRedesign → 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("[门控] needRedesign=true，重新执行设计循环，chainCode={}", ctx.chainCode);
            try {
                ctx = designAgent.run(ctx);
                ctx = syncDesignImagesToCdn(ctx);
                ctx = categoryFilterAgent.run(ctx);
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
            // 即使不生产也记录偏好（选图行为有价值）
            preferenceLearningAgent.run(ctx);
            if (ctx.customerId != null) {
                customerProfileAgent.updateProfile(ctx.customerId, ctx);
            }
            return ctx;
        }

        // -------- Phase 3/4/5: 生产 → 上架 → 支付 → 库存 → 分析 --------
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

        // -------- Phase 5: 需求预测 → 生产计划 → 客户分层 --------
        ctx = demandAgent.run(ctx);
        ctx = productionPlanner.run(ctx);
        if (ctx.clientId != null) {
            ctx = clientAgent.run(ctx);
        }

        // -------- Phase 6 / Step 7: 偏好学习 + 画像回写 --------
        ctx = preferenceLearningAgent.run(ctx);
        if (ctx.customerId != null) {
            customerProfileAgent.updateProfile(ctx.customerId, ctx);
        }
        snapshotService.save(ctx, "Phase6[Profile+Learning]");

        log.info("=== ProductionOrchestrator DONE chainCode={} url={} price={} profit={} roi={} " +
                        "category={} style={} confidence={} ===",
                ctx.chainCode, ctx.productUrl, ctx.price, ctx.profit, ctx.roi,
                ctx.category, ctx.style, ctx.confidenceScore);
        return ctx;
    }

    // ====================================================================
    // CDN helpers
    // ====================================================================

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
