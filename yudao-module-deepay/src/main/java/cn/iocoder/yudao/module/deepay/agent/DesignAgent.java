package cn.iocoder.yudao.module.deepay.agent;

import java.util.Arrays;

/**
 * 设计生成 Agent（MVP 使用模拟数据）。
 *
 * <p>职责：根据用户的 prompt，调用 AI 生图服务（MVP 阶段用固定 mock URL），
 * 将候选图片列表写入 {@link Context#images}。</p>
 *
 * <p>后续接入真实 AI（如 FLUX）时，只需替换本类实现，无需改动 Orchestrator 或其他 Agent。</p>
 */
public class DesignAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        // MVP：模拟 AI 返回 3 张设计候选图片 URL
        ctx.images = Arrays.asList(
                "https://deepay-assets.example.com/designs/" + sanitize(ctx.prompt) + "/v1.jpg",
                "https://deepay-assets.example.com/designs/" + sanitize(ctx.prompt) + "/v2.jpg",
                "https://deepay-assets.example.com/designs/" + sanitize(ctx.prompt) + "/v3.jpg"
        );
        return ctx;
    }

    /** 将 prompt 转成 URL 安全的简单字符串（仅保留字母数字与下划线）。 */
    private String sanitize(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return "default";
        }
        return prompt.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

}
