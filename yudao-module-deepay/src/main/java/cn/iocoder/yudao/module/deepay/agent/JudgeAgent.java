package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignVersionDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JudgeAgent — 给每张设计图打分，结果落库 deepay_design_version（可追溯）。
 *
 * <p>打分规则（MVP）：每张固定 80 分，后续接入真实 AI 视觉评分模型时只替换此类。</p>
 * <p>每次 REDESIGN 版本号自动 +1，保证历史版本完整保留。</p>
 */
@Component
public class JudgeAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(JudgeAgent.class);

    @Resource
    private DeepayDesignVersionMapper deepayDesignVersionMapper;

    @Override
    public Context run(Context ctx) {
        Map<String, Integer> scores = new HashMap<>();

        if (ctx.designImages != null) {
            // 版本号 = 当前 chainCode 已有记录数 + 1（支持 REDESIGN 多轮追溯）
            int baseVersion = ctx.chainCode != null
                    ? deepayDesignVersionMapper.countByChainCode(ctx.chainCode) + 1
                    : 1;

            int idx = 0;
            for (String img : ctx.designImages) {
                int score = 80; // TODO: 替换为真实 AI 视觉评分
                scores.put(img, score);

                DeepayDesignVersionDO ver = new DeepayDesignVersionDO();
                ver.setChainCode(ctx.chainCode);
                ver.setImageUrl(img);
                ver.setVersion(baseVersion + idx);
                ver.setSelected(false);
                ver.setCreatedAt(LocalDateTime.now());
                deepayDesignVersionMapper.insert(ver);
                idx++;
            }
        }

        ctx.imageScores = scores;
        log.info("JudgeAgent: 打分完成，共 {} 张，chainCode={}", scores.size(), ctx.chainCode);
        return ctx;
    }

}


