package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PricingAgent — Phase 4 利润定价。
 *
 * <p>优先级：
 * <ol>
 *   <li><b>利润定价</b>（Phase 4 核心）：{@code price = costPrice × (1 + targetProfitRate)}。
 *       确保每一件都有明确利润边际。</li>
 *   <li><b>AI 建议价</b>（fallback）：若 costPrice 不可用，使用 AIDecisionAgent 输出的 suggestPrice。</li>
 *   <li><b>默认价格</b>（最终 fallback）：{@code deepay.pricing.default-price}，默认 299。</li>
 * </ol>
 * </p>
 *
 * <p>配置项（application.yml）：
 * <pre>
 * deepay:
 *   pricing:
 *     default-cost:         80     # 默认生产成本（元），ProductAgent 未设置时使用
 *     target-profit-rate:   0.6    # 目标利润率（60%），即 price = cost × 1.6
 *     default-price:        299    # 无成本数据时的最终 fallback 价格
 * </pre>
 * </p>
 */
@Component
public class PricingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PricingAgent.class);

    @Value("${deepay.pricing.default-cost:80}")
    private BigDecimal defaultCost;

    @Value("${deepay.pricing.target-profit-rate:0.6}")
    private BigDecimal targetProfitRate;

    @Value("${deepay.pricing.default-price:299}")
    private BigDecimal defaultPrice;

    @Resource
    private DeepayProductMapper deepayProductMapper;

    @Override
    public Context run(Context ctx) {
        BigDecimal cost = resolveCost(ctx);
        ctx.costPrice = cost;

        // Phase 4 利润定价：price = cost × (1 + targetProfitRate)
        BigDecimal profitPrice = cost.multiply(BigDecimal.ONE.add(targetProfitRate))
                .setScale(2, RoundingMode.HALF_UP);

        ctx.price = profitPrice;

        if (ctx.productId != null) {
            deepayProductMapper.updatePrice(Long.parseLong(ctx.productId), ctx.price);
            deepayProductMapper.updateCostPrice(Long.parseLong(ctx.productId), ctx.costPrice);
        }

        log.info("PricingAgent: 利润定价完成 cost={} profitRate={} price={} productId={}",
                cost, targetProfitRate, ctx.price, ctx.productId);
        return ctx;
    }

    /**
     * 解析成本：优先使用 Context 已有值，否则使用配置默认值。
     */
    private BigDecimal resolveCost(Context ctx) {
        if (ctx.costPrice != null && ctx.costPrice.compareTo(BigDecimal.ZERO) > 0) {
            return ctx.costPrice;
        }
        return defaultCost;
    }

}

