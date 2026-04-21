package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignVersionDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignVersionMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 决策规则：取最高分图片，分数 &lt; 60 则触发 REDESIGN；分数 ≥ 60 进入生产。
 * 建议售价 = 同品类历史均价（无历史时 fallback 到 299）。
 * </p>
 */
@Component
public class AIDecisionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AIDecisionAgent.class);

    private static final int REDESIGN_THRESHOLD = 60;
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("299");

    /**
     * Phase 4 ROI 门控：预期 ROI 低于此阈值时不生产，避免"越卖越亏"。
     * ROI = profit / cost = (price - cost) / cost。
     * 以 targetProfitRate=0.6 定价时 ROI=0.6；此阈值=0.1 是宽松的安全下线。
     */
    private static final BigDecimal MIN_ROI_THRESHOLD = new BigDecimal("0.1");

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Resource
    private DeepayDesignVersionMapper deepayDesignVersionMapper;

    @Resource
    private DeepayMetricsMapper deepayMetricsMapper;

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
        ctx.suggestPrice  = computeSuggestPrice(ctx.keyword);

        if (bestScore < REDESIGN_THRESHOLD) {
            ctx.needRedesign  = true;
            ctx.shouldProduce = false;
            ctx.action        = "REDESIGN";
            ctx.decisionReason = "最高分=" + bestScore + "，低于阈值" + REDESIGN_THRESHOLD + "，触发重新设计";
        } else {
            ctx.needRedesign  = false;

            // Phase 4 ROI 门控：预估 ROI 低于最低阈值时拒绝生产
            BigDecimal estimatedRoi = estimateRoi(ctx);
            if (estimatedRoi != null && estimatedRoi.compareTo(MIN_ROI_THRESHOLD) < 0) {
                ctx.shouldProduce = false;
                ctx.action        = "STOP";
                ctx.decisionReason = "最高分=" + bestScore + "，但预估ROI=" + estimatedRoi
                        + " 低于最低阈值" + MIN_ROI_THRESHOLD + "，不值得生产";
            } else {
                ctx.shouldProduce = true;
                ctx.action        = "BOOST";
                ctx.decisionReason = "最高分=" + bestScore + "，选中图片=" + best
                        + "，预估ROI=" + estimatedRoi + "，建议售价=" + ctx.suggestPrice;
            }
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

        log.info("AIDecisionAgent: action={} suggestPrice={} reason={}", ctx.action, ctx.suggestPrice, ctx.decisionReason);
        return ctx;
    }

    /**
     * 根据同品类历史均价计算建议售价。
     *
     * @param keyword 商品关键词（品类）
     * @return 建议售价；无历史数据时返回 299
     */
    private BigDecimal computeSuggestPrice(String keyword) {
        if (keyword != null) {
            try {
                BigDecimal avgPrice = deepayMetricsMapper.selectAvgPriceByCategory(keyword);
                if (avgPrice != null && avgPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal price = avgPrice.setScale(2, RoundingMode.HALF_UP);
                    log.info("AIDecisionAgent: 品类[{}] 历史均价={}, 采用为建议售价", keyword, price);
                    return price;
                }
            } catch (Exception e) {
                log.warn("AIDecisionAgent: 查询历史均价失败，使用默认价格 {}", DEFAULT_PRICE, e);
            }
        }
        log.info("AIDecisionAgent: 品类[{}] 无历史价格数据，使用默认价格 {}", keyword, DEFAULT_PRICE);
        return DEFAULT_PRICE;
    }

    /**
     * Phase 4 — 预估本次生产的 ROI。
     * 公式：ROI = (suggestPrice - costPrice) / costPrice
     * 若缺少任何值则返回 null（不做 ROI 门控）。
     */
    private BigDecimal estimateRoi(Context ctx) {
        BigDecimal price = ctx.suggestPrice;
        BigDecimal cost  = ctx.costPrice;
        if (price == null || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        try {
            return price.subtract(cost).divide(cost, 4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("AIDecisionAgent: 估算 ROI 失败", e);
            return null;
        }
    }

}


