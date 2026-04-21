package cn.iocoder.yudao.module.deepay.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * JudgeAgent — 给设计图打分，先写死 80 分。
 */
public class JudgeAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        Map<String, Integer> scores = new HashMap<>();
        if (ctx.designImages != null) {
            for (String img : ctx.designImages) {
                scores.put(img, 80);
            }
        }
        ctx.imageScores = scores;
        return ctx;
    }

}

