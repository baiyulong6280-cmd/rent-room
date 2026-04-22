package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignImageDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayFeedbackDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignImageMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayFeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FeedbackAgent — 记录用户选图反馈并更新图片评分（Phase 8）。
 *
 * <p>对每张评分图：
 * <ul>
 *   <li>写入 deepay_feedback（selected=1 表示被用户选中）</li>
 *   <li>更新 deepay_design_image 评分：选中 +10，未选中 score = max(0, score-5)</li>
 * </ul>
 * </p>
 */
@Component
public class FeedbackAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(FeedbackAgent.class);

    /** 用户选中图片的分数加成 */
    private static final double SELECTED_BONUS   = 10.0;
    /** 用户未选中图片的分数扣减 */
    private static final double UNSELECTED_PENALTY = 5.0;

    @Resource
    private DeepayFeedbackMapper feedbackMapper;

    @Resource
    private DeepayDesignImageMapper designImageMapper;

    @Override
    public Context run(Context ctx) {
        try {
            if (ctx.scoredImages == null || ctx.scoredImages.isEmpty()) {
                log.info("[FeedbackAgent] 无评分图，跳过反馈记录");
                return ctx;
            }

            String userId = resolveUserId(ctx);
            String selectedUrl = ctx.selectedImage;

            for (DesignImage img : ctx.scoredImages) {
                int selected = StringUtils.hasText(selectedUrl) && selectedUrl.equals(img.getUrl()) ? 1 : 0;

                // 写入反馈记录
                saveFeedback(img.getUrl(), userId, selected);

                // 更新 deepay_design_image 分数
                updateImageScore(img.getUrl(), selected);
            }

            log.info("[FeedbackAgent] 反馈记录完成 total={} selectedUrl={}",
                    ctx.scoredImages.size(), selectedUrl);
        } catch (Exception e) {
            log.warn("[FeedbackAgent] 反馈记录异常，跳过", e);
        }
        return ctx;
    }

    private void saveFeedback(String imageUrl, String userId, int selected) {
        try {
            DeepayFeedbackDO record = new DeepayFeedbackDO();
            record.setImageUrl(imageUrl);
            record.setUserId(userId);
            record.setSelected(selected);
            record.setCreatedAt(LocalDateTime.now());
            feedbackMapper.insert(record);
        } catch (Exception e) {
            log.warn("[FeedbackAgent] 写入 deepay_feedback 失败 url={}", imageUrl, e);
        }
    }

    private void updateImageScore(String imageUrl, int selected) {
        try {
            // 先查出该图片记录
            List<DeepayDesignImageDO> records = designImageMapper.selectList(
                    new LambdaQueryWrapper<DeepayDesignImageDO>()
                            .eq(DeepayDesignImageDO::getUrl, imageUrl)
                            .orderByDesc(DeepayDesignImageDO::getCreatedAt)
                            .last("LIMIT 1"));
            if (records.isEmpty()) {
                return;
            }
            DeepayDesignImageDO record = records.get(0);
            double currentScore = record.getScore() != null ? record.getScore() : 0;

            double newScore;
            if (selected == 1) {
                newScore = currentScore + SELECTED_BONUS;
            } else {
                newScore = Math.max(0, currentScore - UNSELECTED_PENALTY);
            }

            designImageMapper.update(null, new LambdaUpdateWrapper<DeepayDesignImageDO>()
                    .eq(DeepayDesignImageDO::getId, record.getId())
                    .set(DeepayDesignImageDO::getScore, newScore));
        } catch (Exception e) {
            log.warn("[FeedbackAgent] 更新 deepay_design_image 分数失败 url={}", imageUrl, e);
        }
    }

    private String resolveUserId(Context ctx) {
        if (ctx.userId != null)     return String.valueOf(ctx.userId);
        if (ctx.customerId != null) return String.valueOf(ctx.customerId);
        return null;
    }

}
