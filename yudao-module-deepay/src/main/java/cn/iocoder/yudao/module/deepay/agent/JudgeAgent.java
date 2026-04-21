package cn.iocoder.yudao.module.deepay.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * 视觉评分 Agent（JudgeAgent）。
 *
 * <p>职责：对 {@link Context#images} 中的每张候选图片打分（0-100），
 * 将结果写入 {@link Context#imageScores}（图片列表下标 → 评分）。</p>
 *
 * <p>MVP 策略：基于图片 URL 的哈希值生成确定性评分，保证同一图片每次得分一致，
 * 同时制造分数差异以驱动 {@link AIDecisionAgent} 的选择逻辑。</p>
 */
public class JudgeAgent implements Agent {

    /** 评分最低保底分 */
    private static final int MIN_SCORE = 50;
    /** 评分随机区间 */
    private static final int SCORE_RANGE = 50;

    @Override
    public Context run(Context ctx) {
        if (ctx.images == null || ctx.images.isEmpty()) {
            ctx.imageScores = new HashMap<>();
            return ctx;
        }

        Map<Integer, Integer> scores = new HashMap<>();
        for (int i = 0; i < ctx.images.size(); i++) {
            int score = scoreImage(ctx.images.get(i), i);
            scores.put(i, score);
        }
        ctx.imageScores = scores;
        return ctx;
    }

    /**
     * 对单张图片评分：基于 URL 哈希 + 下标偏移，范围 [MIN_SCORE, MIN_SCORE + SCORE_RANGE]。
     */
    private int scoreImage(String imageUrl, int index) {
        int hash = Math.abs(imageUrl.hashCode());
        return MIN_SCORE + ((hash + index * 7) % (SCORE_RANGE + 1));
    }

}
