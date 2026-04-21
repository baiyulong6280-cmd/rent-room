package cn.iocoder.yudao.module.deepay.agent;

import java.util.Map;

/**
 * AI 核心决策 Agent（AIDecisionAgent）🔥
 *
 * <p>职责：根据 {@link JudgeAgent} 的视觉评分，从 {@link Context#images} 中
 * 选出得分最高的图片，写入 {@link Context#selectedImage}。</p>
 *
 * <p>相比原 {@link DecisionAgent}（固定选第一张），本 Agent 基于评分动态选图，
 * 是整条流水线的核心决策环节。</p>
 */
public class AIDecisionAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        if (ctx.images == null || ctx.images.isEmpty()) {
            throw new IllegalStateException("AIDecisionAgent: images 列表为空，无法执行决策");
        }

        // 有评分数据时选高分图，否则退化为选第一张
        if (ctx.imageScores != null && !ctx.imageScores.isEmpty()) {
            ctx.selectedImage = selectHighestScoredImage(ctx);
        } else {
            ctx.selectedImage = ctx.images.get(0);
        }
        return ctx;
    }

    /**
     * 从 imageScores 中找出得分最高的图片下标，返回对应 URL。
     */
    private String selectHighestScoredImage(Context ctx) {
        int bestIndex = 0;
        int bestScore = -1;
        for (Map.Entry<Integer, Integer> entry : ctx.imageScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestIndex = entry.getKey();
            }
        }
        // 防止下标越界
        if (bestIndex >= ctx.images.size()) {
            bestIndex = 0;
        }
        return ctx.images.get(bestIndex);
    }

}
