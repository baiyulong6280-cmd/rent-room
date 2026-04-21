package cn.iocoder.yudao.module.deepay.orchestrator;

import cn.iocoder.yudao.module.deepay.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 完整生产流水线编排器（Orchestrator）🔥
 *
 * <p>按顺序串行执行 11 个 Agent，覆盖从「找爆款」到「复盘优化」的完整闭环：</p>
 *
 * <pre>
 * 找爆款    → TrendAgent
 * 改款      → DesignAgent
 * 评估      → JudgeAgent
 * 决策      → AIDecisionAgent
 * 链码      → ChainAgent
 * 打版      → PatternAgent
 * 商品生成  → ProductAgent
 * 定价      → PricingAgent
 * 上架      → PublishAgent
 * 收款      → PaymentAgent
 * 库存      → InventoryAgent
 * 复盘      → AnalyticsAgent
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>所有步骤同步串行，不引入异步机制（保持简单可调试）。</li>
 *   <li>每步异常均向上抛出并附加步骤名称，便于定位。</li>
 *   <li>ImaAgent（ima 知识库同步）作为可选副本步骤，失败不中断主流程。</li>
 * </ul>
 * </p>
 */
@Service
public class ProductionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrchestrator.class);

    // ---- 需要 Spring 管理的 Bean（依赖外部服务 / 数据库）----
    @Resource private TrendAgent    trendAgent;
    @Resource private DesignAgent   designAgent;
    @Resource private ChainAgent    chainAgent;
    @Resource private ProductAgent  productAgent;
    @Resource private PublishAgent  publishAgent;
    @Resource private InventoryAgent inventoryAgent;
    @Resource private ImaAgent      imaAgent;

    /**
     * 执行完整生产流水线。
     *
     * @param prompt 用户输入的一句话需求，例如"极简羊绒大衣"
     * @return 填充完毕的 {@link Context}，包含链码、收款链接、复盘报告等
     */
    public Context run(String prompt) {
        Context ctx = new Context();
        ctx.prompt = prompt;

        // 1. 找爆款：从 prompt 中识别趋势关键词
        ctx = runStep("TrendAgent",     () -> trendAgent.run(ctx));

        // 2. 改款：调用 AI 生图服务生成候选图片
        ctx = runStep("DesignAgent",    () -> designAgent.run(ctx));

        // 3. 评估：视觉打分（无外部依赖，直接 new）
        ctx = runStep("JudgeAgent",     () -> new JudgeAgent().run(ctx));

        // 4. 决策：选出最高分图片（无外部依赖，直接 new）
        ctx = runStep("AIDecisionAgent",() -> new AIDecisionAgent().run(ctx));

        // 5. 链码：生成唯一链码并落库
        ctx = runStep("ChainAgent",     () -> chainAgent.run(ctx));

        // 6. 打版：生成版型编码（无外部依赖，直接 new）
        ctx = runStep("PatternAgent",   () -> new PatternAgent().run(ctx));

        // 7. 商品生成：将打版/趋势信息写库，获取 productRecordId
        ctx = runStep("ProductAgent",   () -> productAgent.run(ctx));

        // 8. 定价：规则计算售价（无外部依赖，直接 new）
        ctx = runStep("PricingAgent",   () -> new PricingAgent().run(ctx));

        // 9. 上架：更新状态为 PUBLISHED
        ctx = runStep("PublishAgent",   () -> publishAgent.run(ctx));

        // 10. 收款：生成 Jeepay + Swan 双通道链接（无外部依赖，直接 new）
        ctx = runStep("PaymentAgent",   () -> new PaymentAgent().run(ctx));

        // 11. 库存：设置初始库存并批量回填 DB
        ctx = runStep("InventoryAgent", () -> inventoryAgent.run(ctx));

        // 11.5 ima 同步（可选副本，失败不中断）
        imaAgent.run(ctx);

        // 12. 复盘：生成摘要报告（无外部依赖，直接 new）
        ctx = runStep("AnalyticsAgent", () -> new AnalyticsAgent().run(ctx));

        log.info("ProductionOrchestrator 完成，chainCode={}", ctx.chainCode);
        return ctx;
    }

    /**
     * 包装单步执行，统一记录日志并将异常附加步骤名称后重抛。
     */
    private Context runStep(String stepName, StepSupplier supplier) {
        log.debug("[{}] 开始执行", stepName);
        try {
            Context result = supplier.get();
            log.debug("[{}] 执行完成", stepName);
            return result;
        } catch (IllegalStateException e) {
            throw e; // 业务异常直接透传
        } catch (Exception e) {
            throw new RuntimeException("[" + stepName + "] 执行失败: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface StepSupplier {
        Context get();
    }

}
