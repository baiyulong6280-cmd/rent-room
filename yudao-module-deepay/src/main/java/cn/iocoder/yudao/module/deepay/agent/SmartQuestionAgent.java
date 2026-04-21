package cn.iocoder.yudao.module.deepay.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * SmartQuestionAgent — 一次性问卷（Phase 6 任务 3）。
 *
 * <p>触发条件（由 Orchestrator 决定）：
 * <pre>
 *   if (profile == null || ctx.confidenceScore &lt; 0.6) {
 *       ctx = smartQuestionAgent.run(ctx)
 *   }
 * </pre>
 * </p>
 *
 * <p>只在 Context 字段为空时填充默认值（不覆盖调用方已填入的值），
 * 使调用方可以通过 API 直接传入答案来"代替"用户手动回答。</p>
 *
 * <p>四个问题对应四个字段：
 * <ol>
 *   <li>做什么类目？ → {@link Context#category}</li>
 *   <li>卖哪里？ → {@link Context#market}</li>
 *   <li>什么风格？ → {@link Context#style}</li>
 *   <li>什么价位？ → {@link Context#priceLevel}</li>
 * </ol>
 * 回答完毕后，confidenceScore 升至 0.6（初始门槛），下次跑不再触发问卷。</p>
 *
 * <p>❌ 不对接前端 / 不阻塞请求 — 所有问题的答案通过 API 入参或画像预填充传入，
 * SmartQuestionAgent 仅做"缺什么补什么"。</p>
 */
@Component
public class SmartQuestionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SmartQuestionAgent.class);

    // ---- 各维度的合法值集合 ----
    private static final List<String> VALID_MARKETS     = Arrays.asList("CN", "EU", "US", "ME");
    private static final List<String> VALID_PRICE_LEVELS = Arrays.asList("LOW", "MID", "HIGH");
    private static final List<String> VALID_GENDERS     = Arrays.asList("MALE", "FEMALE", "UNISEX");
    private static final List<String> VALID_AGES        = Arrays.asList("YOUNG", "MIDDLE", "ELDER");

    /** 置信度门槛：低于此值需触发问卷 */
    public static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.6");

    @Override
    public Context run(Context ctx) {
        log.info("[SmartQuestionAgent] 触发问卷补填，customerId={} confidence={}",
                ctx.customerId, ctx.confidenceScore);

        // 问题 1：做什么类目？
        if (!StringUtils.hasText(ctx.category)) {
            ctx.category = "通用";   // 默认值（真实场景由前端传入或 API 参数注入）
            log.info("[SmartQuestionAgent] Q1 category 未填，设置默认值='{}'", ctx.category);
        }

        // keyword 与 category 保持同步（DesignAgent / JudgeAgent 使用 keyword）
        if (!StringUtils.hasText(ctx.keyword)) {
            ctx.keyword = ctx.category;
        }

        // 问题 2：卖哪里？
        if (!StringUtils.hasText(ctx.market) || !VALID_MARKETS.contains(ctx.market)) {
            ctx.market = "CN";
            log.info("[SmartQuestionAgent] Q2 market 未填或非法，设置默认值='{}'", ctx.market);
        }

        // 问题 3：什么风格？
        if (!StringUtils.hasText(ctx.style)) {
            ctx.style = "minimalist";
            log.info("[SmartQuestionAgent] Q3 style 未填，设置默认值='{}'", ctx.style);
        }

        // 问题 4：什么价位？
        if (!StringUtils.hasText(ctx.priceLevel) || !VALID_PRICE_LEVELS.contains(ctx.priceLevel)) {
            ctx.priceLevel = "MID";
            log.info("[SmartQuestionAgent] Q4 priceLevel 未填或非法，设置默认值='{}'", ctx.priceLevel);
        }

        // 补充可选维度（不强制）
        if (!StringUtils.hasText(ctx.gender) || !VALID_GENDERS.contains(ctx.gender)) {
            ctx.gender = "UNISEX";
        }
        if (!StringUtils.hasText(ctx.targetAge) || !VALID_AGES.contains(ctx.targetAge)) {
            ctx.targetAge = "YOUNG";
        }

        // 填充完成后，将置信度提升到 0.6（初始门槛），后续 PreferenceLearning 会继续累加
        if (ctx.confidenceScore == null || ctx.confidenceScore.compareTo(CONFIDENCE_THRESHOLD) < 0) {
            ctx.confidenceScore = CONFIDENCE_THRESHOLD;
        }

        log.info("[SmartQuestionAgent] 问卷补填完成: category={} market={} style={} priceLevel={} confidence={}",
                ctx.category, ctx.market, ctx.style, ctx.priceLevel, ctx.confidenceScore);
        return ctx;
    }

    /**
     * 判断是否需要触发问卷（由 Orchestrator 调用）。
     *
     * @param ctx 当前 Context
     * @return true = 需要触发 SmartQuestionAgent
     */
    public static boolean needsQuestionnaire(Context ctx) {
        return ctx.confidenceScore == null
                || ctx.confidenceScore.compareTo(CONFIDENCE_THRESHOLD) < 0;
    }

}
