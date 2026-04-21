package cn.iocoder.yudao.module.deepay.agent;

import java.math.BigDecimal;

/**
 * AIDecisionAgent — 核心决策：选图 / 是否改款 / 是否生产 / 建议价格。
 * 先 mock，后续接入真实 AI 评分模型。
 */
public class AIDecisionAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        if (ctx.designImages == null || ctx.designImages.isEmpty()) {
            throw new IllegalStateException("AIDecisionAgent: designImages 为空");
        }
        ctx.selectedImage  = ctx.designImages.get(0);
        ctx.needRedesign   = false;
        ctx.shouldProduce  = true;
        ctx.suggestPrice   = new BigDecimal("299");
        ctx.action         = "BOOST";
        return ctx;
    }

}

