package cn.iocoder.yudao.module.deepay.agent;

import java.util.Arrays;
import java.util.List;

/**
 * AI 定价 Agent（PricingAgent）。
 *
 * <p>职责：根据趋势关键词和打版编码计算商品售价，写入 {@link Context#price}（单位：分）。</p>
 *
 * <p>MVP 定价规则（写死，后续可接入 AI 动态定价模型）：
 * <ul>
 *   <li>基础价：299 元（29900 分）</li>
 *   <li>高端材质溢价（羊绒 / 皮草 / 真丝 / 羊毛）：+100 元</li>
 *   <li>热门风格溢价（极简 / 法式 / 小香风）：+50 元</li>
 * </ul>
 * </p>
 */
public class PricingAgent implements Agent {

    /** 基础售价：299 元，单位分 */
    private static final int BASE_PRICE_FEN = 29900;

    /** 高端材质关键词 → 溢价 100 元 */
    private static final List<String> PREMIUM_MATERIALS = Arrays.asList("羊绒", "皮草", "真丝", "羊毛");

    /** 热门风格关键词 → 溢价 50 元 */
    private static final List<String> TREND_STYLES = Arrays.asList("极简", "法式", "小香风", "vintage");

    @Override
    public Context run(Context ctx) {
        int price = BASE_PRICE_FEN;

        String keyword = ctx.trendKeyword != null ? ctx.trendKeyword : "";

        // 高端材质溢价
        boolean isPremium = PREMIUM_MATERIALS.stream().anyMatch(keyword::contains);
        if (isPremium) {
            price += 10000; // +100 元
        }

        // 热门风格溢价
        boolean isTrend = TREND_STYLES.stream().anyMatch(keyword::contains);
        if (isTrend) {
            price += 5000; // +50 元
        }

        ctx.price = price;
        return ctx;
    }

}
