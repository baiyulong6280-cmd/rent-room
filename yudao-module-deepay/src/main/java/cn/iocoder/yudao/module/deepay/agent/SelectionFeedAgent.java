package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayTrendItemDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayTrendItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SelectionFeedAgent — 外部趋势款选品注入（Phase 9）。
 *
 * <p>从 {@code deepay_trend_item} 表读取来自外部平台（1688 / TikTok / SHEIN / 品牌）
 * 的热门款式，将其注入 {@link Context#trendItems} 和 {@link Context#referenceImages}，
 * 为 TrendAgent 和 AIDecisionAgent 提供外部选品数据源。</p>
 *
 * <p>执行时机：在 TrendAgent 之前运行（ChainOrchestrator 或 ProductionOrchestrator 中配置），
 * 使后续 Agent 能同时看到内部热销款和外部趋势款。</p>
 *
 * <p>优先级规则：
 * <ol>
 *   <li>有 ctx.category → 按品类过滤，取 Top {@value #TOP_N} 热度款</li>
 *   <li>无 ctx.category → 取全品类 Top {@value #TOP_N}</li>
 *   <li>数据库无数据 → 不修改 ctx，静默跳过（不影响主流程）</li>
 * </ol>
 * </p>
 *
 * <p>验收：
 * <ul>
 *   <li>✔ ctx.trendItems 包含外部趋势款（imageUrl / category / style 字段已填充）</li>
 *   <li>✔ ctx.referenceImages 追加了外部趋势图 URL（与内部热销图合并）</li>
 *   <li>✔ 无数据时不抛异常，主流程正常运行</li>
 * </ul>
 * </p>
 */
@Component
public class SelectionFeedAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SelectionFeedAgent.class);

    /** 每次从外部趋势款中取的最大数量 */
    private static final int TOP_N = 10;

    @Resource
    private DeepayTrendItemMapper trendItemMapper;

    @Override
    public Context run(Context ctx) {
        log.info("[SelectionFeedAgent] START category={}", ctx.category);

        List<DeepayTrendItemDO> externalItems = fetchExternalItems(ctx.category);

        if (externalItems.isEmpty()) {
            log.info("[SelectionFeedAgent] 无外部趋势款数据，跳过注入 category={}", ctx.category);
            return ctx;
        }

        // 1. 将外部趋势款转换为 TrendItem 写入 ctx.trendItems
        List<TrendItem> externalTrendItems = externalItems.stream()
                .map(this::toTrendItem)
                .collect(Collectors.toList());

        if (ctx.trendItems == null) {
            ctx.trendItems = new ArrayList<>();
        }
        ctx.trendItems.addAll(externalTrendItems);

        // 2. 将图片 URL 追加到 referenceImages
        List<String> externalImageUrls = externalItems.stream()
                .map(DeepayTrendItemDO::getImageUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        if (!externalImageUrls.isEmpty()) {
            if (ctx.referenceImages == null) {
                ctx.referenceImages = new ArrayList<>();
            }
            ctx.referenceImages.addAll(externalImageUrls);
        }

        log.info("[SelectionFeedAgent] DONE 注入外部趋势款 count={} category={}",
                externalItems.size(), ctx.category);
        return ctx;
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    private List<DeepayTrendItemDO> fetchExternalItems(String category) {
        try {
            return trendItemMapper.selectTopByCategory(category, TOP_N);
        } catch (Exception e) {
            log.warn("[SelectionFeedAgent] 查询外部趋势款异常，跳过注入", e);
            return java.util.Collections.emptyList();
        }
    }

    private TrendItem toTrendItem(DeepayTrendItemDO do_) {
        TrendItem item = new TrendItem();
        item.setImageUrl(do_.getImageUrl());
        item.setCategory(do_.getCategory());
        item.setStyle(do_.getStyle());
        // heatScore 用作 soldCount 的近似值，用于后续风格加权排序
        item.setSoldCount(do_.getHeatScore() != null ? do_.getHeatScore() : 0);
        // 外部趋势款无链码，用 source+id 组合作为唯一标识
        item.setChainCode("ext-" + do_.getSource() + "-" + do_.getId());
        return item;
    }

}
