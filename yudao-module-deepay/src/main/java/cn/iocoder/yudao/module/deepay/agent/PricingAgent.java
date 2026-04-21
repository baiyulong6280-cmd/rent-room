package cn.iocoder.yudao.module.deepay.agent;

import java.math.BigDecimal;

/**
 * PricingAgent — 设置商品价格。
 * 优先使用 AIDecisionAgent 的建议价，否则默认 299。
 */
public class PricingAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        if (ctx.suggestPrice != null) {
            ctx.price = ctx.suggestPrice;
        } else {
            ctx.price = new BigDecimal("299");
        }
        return ctx;
    }

}

