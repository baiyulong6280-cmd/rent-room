package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignVersionDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignVersionMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

/**
 * AIDecisionAgent — 核心决策：选图 / 是否改款 / 是否生产 / 建议价格。
 *
 * <p>原则：
 * <ol>
 *   <li><b>可解释</b> — 每次决策原因写入 {@link Context#decisionReason} 并落库 deepay_style_chain。</li>
 *   <li><b>可追溯</b> — 选中图片回写 deepay_design_version.selected=true。</li>
 *   <li><b>可替换</b> — 评分逻辑集中在本类，接入真实 AI 模型时只替换此类。</li>
 * </ol>
 * 当前 MVP：取最高分图片，分数 &lt; 60 则触发 REDESIGN；分数 ≥ 60 进入生产，建议价 299。
 * </p>
 */
@Component
public class AIDecisionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AIDecisionAgent.class);

    private static final int REDESIGN_THRESHOLD = 60;

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Resource
    private DeepayDesignVersionMapper deepayDesignVersionMapper;

    @Override
    public Context run(Context ctx) {
        if (ctx.designImages == null || ctx.designImages.isEmpty()) {
            throw new IllegalStateException("AIDecisionAgent: designImages 为空");
        }

        // 选最高分图
        String best = ctx.designImages.get(0);
        int   bestScore = 0;
        if (ctx.imageScores != null) {
            for (Map.Entry<String, Integer> e : ctx.imageScores.entrySet()) {
                if (e.getValue() > bestScore) {
                    bestScore = e.getValue();
                    best      = e.getKey();
                }
            }
        }

        ctx.selectedImage = best;
        ctx.suggestPrice  = new BigDecimal("299");

        if (bestScore < REDESIGN_THRESHOLD) {
            ctx.needRedesign  = true;
            ctx.shouldProduce = false;
            ctx.action        = "REDESIGN";
            ctx.decisionReason = "最高分=" + bestScore + "，低于阈值" + REDESIGN_THRESHOLD + "，触发重新设计";
        } else {
            ctx.needRedesign  = false;
            ctx.shouldProduce = true;
            ctx.action        = "BOOST";
            ctx.decisionReason = "最高分=" + bestScore + "，选中图片=" + best + "，建议售价=" + ctx.suggestPrice;
        }

        // 落库：回写 deepay_style_chain 决策结果（可解释）
        if (ctx.chainCode != null) {
            deepayStyleChainMapper.update(null, new LambdaUpdateWrapper<DeepayStyleChainDO>()
                    .eq(DeepayStyleChainDO::getChainCode, ctx.chainCode)
                    .set(DeepayStyleChainDO::getSelectedImage, ctx.selectedImage)
                    .set(DeepayStyleChainDO::getDecisionReason, ctx.decisionReason));
        }

        // 落库：标记选中图版本（可追溯）
        if (ctx.chainCode != null) {
            deepayDesignVersionMapper.update(null, new LambdaUpdateWrapper<DeepayDesignVersionDO>()
                    .eq(DeepayDesignVersionDO::getChainCode, ctx.chainCode)
                    .eq(DeepayDesignVersionDO::getImageUrl, best)
                    .set(DeepayDesignVersionDO::getSelected, true));
        }

        log.info("AIDecisionAgent: action={} reason={}", ctx.action, ctx.decisionReason);
        return ctx;
    }

}


