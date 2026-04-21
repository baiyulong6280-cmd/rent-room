package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import cn.iocoder.yudao.module.deepay.scheduler.DeepayRetryScheduler;
import cn.iocoder.yudao.module.deepay.service.CdnService;
import cn.iocoder.yudao.module.deepay.service.ContextSnapshotService;
import cn.iocoder.yudao.module.deepay.service.DeepayAuditService;
import cn.iocoder.yudao.module.deepay.service.StyleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * ProductionOrchestrator — 全链路唯一调度器（Phase 6 最终版）。
 *
 * <pre>
 * ===== 完整流程 =====
 *
 * 1. MemoryAgent           — 加载用户画像（category / style / market / styleWeights）
 * 2. SmartQuestionAgent    — 决策树：缺字段则设 ctx.pendingQuestion 并立即返回
 *    ↳ pendingQuestion != null → 返回给调用方，等待用户回答后再次调用
 *    MemoryAgent.save      — 回写本次补填的答案
 *
 * 3. ChainAgent            — 生成 chainCode
 *
 * 4. TrendAgent            — 只查 ctx.category 的内部热销 → referenceImages（品类强过滤）
 *    CDN sync
 *
 * 5. DesignAgent           — 个性化 prompt（category + stylePrompt + market + trend）
 *    CDN sync
 *    CategoryFilterAgent   — 二次品类过滤（防错核心）
 *    JudgeAgent            — 历史加权打分
 *    AIDecisionAgent       — 品类+风格过滤后选最高分（ROI 门控）
 *    [snapshot]
 *    if needRedesign → 重跑 Design 循环
 *    if !shouldProduce → 终止
 *
 * 6. PatternAgent / ProductAgent / PricingAgent / PublishAgent
 *    FinanceAgent / InventoryAgent / AnalyticsAgent
 *    [snapshot]
 *
 * 7. DemandAgent / ProductionPlanner / ClientAgent
 *
 * 8. PreferenceLearningAgent + MemoryAgent.save（偏好学习 + 画像回写）
 *    [snapshot]
 *
 * 验收：
 *   ✔ 不输入关键词也能跑（画像填充 keyword）
 *   ✔ 不会出错类目（TrendAgent WHERE category=? + CategoryFilterAgent 双重防护）
 *   ✔ 缺字段 → 一次只问一个问题（pendingQuestion 机制）
 *   ✔ 客户选一次 → 下次更准（PreferenceLearningAgent 累积）
 *   ✔ 第二次基本不用问（confidence ≥ 0.6 后跳过问卷）
 * </pre>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    // ---- Phase 6 memory + question ----
    @Resource private MemoryAgent             memoryAgent;
    @Resource private SmartQuestionAgent      smartQuestionAgent;
    @Resource private CustomerProfileAgent    customerProfileAgent;
    @Resource private CategoryFilterAgent     categoryFilterAgent;
    @Resource private PreferenceLearningAgent preferenceLearningAgent;

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

    // ---- Services ----
    @Resource private ContextSnapshotService snapshotService;
    @Resource private DeepayAuditService     auditService;
    @Resource private CdnService             cdnService;
    @Resource private DeepayRetryScheduler   retryScheduler;
    @Resource private StyleEngine            styleEngine;

    // ====================================================================
    // 主入口
    // ====================================================================

    public Context run(Context ctx) {
        log.info("=== ProductionOrchestrator START keyword={} userId={} category={} ===",
                ctx.keyword, ctx.userId, ctx.category);

        // -------- Step 1: 记忆加载 --------
        ctx = memoryAgent.run(ctx);                    // 从 deepay_user_profile 填充画像
        // CustomerProfileAgent 补充 B2B 客户画像（兼容旧路径）
        if (ctx.customerId != null) {
            ctx = customerProfileAgent.run(ctx);
        }

        // -------- Step 2: 问答决策树（一次一问）--------
        if (SmartQuestionAgent.needsQuestionnaire(ctx)) {
            ctx = smartQuestionAgent.run(ctx);
            // 如果决策树发现有字段缺失，立即返回问题给调用方
            if (StringUtils.hasText(ctx.pendingQuestion)) {
                log.info("[Orchestrator] ⏸ 等待用户回答 pendingQuestion={}",
                        ctx.pendingQuestion);
                return ctx;                            // 🚨 暂停流程，不继续执行
            }
            // 所有字段已填齐，回写画像（下次跳过问卷）
            memoryAgent.save(ctx);
            if (ctx.customerId != null) {
                customerProfileAgent.updateProfile(ctx.customerId, ctx);
            }
        }

        // -------- Step 3: 链路生成 --------
        ctx = chainAgent.run(ctx);

        // -------- Step 4: 趋势参考图（强品类过滤）--------
        // TrendAgent 内部 WHERE p.category = ctx.category，客户做内裤永远不出外套
        ctx = trendAgent.run(ctx);
        ctx = syncReferenceImagesToCdn(ctx);

        // -------- Step 5: 设计 + 双重品类过滤 + 评审 --------
        // StyleEngine 预组装 stylePrompt，供 DesignAgent 直接消费
        ctx.stylePrompt = styleEngine.buildFullPrompt(ctx);
        try {
            ctx = designAgent.run(ctx);
            ctx = syncDesignImagesToCdn(ctx);
            ctx = categoryFilterAgent.run(ctx);        // 二次品类防错
        } catch (Exception e) {
            log.error("[Orchestrator] DesignAgent 失败，登记重试 chainCode={}", ctx.chainCode, e);
            retryScheduler.register(ctx.chainCode, "AI_DESIGN", e.getMessage());
            return ctx;
        }

        ctx = judgeAgent.run(ctx);
        ctx = aiDecisionAgent.run(ctx);
        snapshotService.save(ctx, "AIDecisionAgent");
        auditService.log(ctx.chainCode, "CREATE",
                "keyword=" + ctx.keyword + " category=" + ctx.category,
                "action=" + ctx.action + " shouldProduce=" + ctx.shouldProduce);

        // 门控：needRedesign → 重跑一次设计循环
        if (Boolean.TRUE.equals(ctx.needRedesign)) {
            log.info("[门控] needRedesign=true，重新执行设计循环 chainCode={}", ctx.chainCode);
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
            log.info("[门控] shouldProduce=false，终止 reason={}", ctx.decisionReason);
            preferenceLearningAgent.run(ctx);
            memoryAgent.save(ctx);
            return ctx;
        }

        // -------- Step 6: 生产 → 上架 → 支付 → 库存 → 分析 --------
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

        // -------- Step 7: 需求预测 → 生产计划 → 客户分层 --------
        ctx = demandAgent.run(ctx);
        ctx = productionPlanner.run(ctx);
        if (ctx.clientId != null) {
            ctx = clientAgent.run(ctx);
        }

        // -------- Step 8: 偏好学习 + 画像回写 --------
        ctx = preferenceLearningAgent.run(ctx);
        memoryAgent.save(ctx);
        if (ctx.customerId != null) {
            customerProfileAgent.updateProfile(ctx.customerId, ctx);
        }
        snapshotService.save(ctx, "Phase6[Memory+Learning]");

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
            ctx.referenceImages = cdnService.syncAllToCdn(ctx.referenceImages);
        }
        return ctx;
    }

    private Context syncDesignImagesToCdn(Context ctx) {
        if (ctx.designImages != null && !ctx.designImages.isEmpty()) {
            ctx.designImages = cdnService.syncAllToCdn(ctx.designImages);
        }
        if (ctx.selectedImage != null) {
            ctx.selectedImage = cdnService.syncToCdn(ctx.selectedImage);
        }
        return ctx;
    }

}
