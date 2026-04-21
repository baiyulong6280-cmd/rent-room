package cn.iocoder.yudao.module.deepay.agent;

/**
 * 设计决策 Agent。
 *
 * <p>职责：从 {@link Context#images} 中选出最终用于生成商品的图片，写入 {@link Context#selectedImage}。</p>
 *
 * <p>MVP 策略：直接选第一张（index 0）。后续可接入真实评分模型或人工审核环节。</p>
 */
public class DecisionAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        if (ctx.images == null || ctx.images.isEmpty()) {
            throw new IllegalStateException("DecisionAgent: images 列表为空，无法选择设计图");
        }
        // MVP：选第一张
        ctx.selectedImage = ctx.images.get(0);
        return ctx;
    }

}
