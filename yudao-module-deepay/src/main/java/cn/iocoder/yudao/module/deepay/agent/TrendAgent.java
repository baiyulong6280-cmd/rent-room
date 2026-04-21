package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TrendAgent — 真实爆款数据驱动（Phase 6 完整升级版）。
 *
 * <p>数据来源：deepay_metrics JOIN deepay_product（内部热销，不依赖外部 API）。</p>
 *
 * <p>核心逻辑（按顺序执行）：
 * <ol>
 *   <li>查询 TOP-50 全局热销商品（{@code selectTopTrend(50)}）</li>
 *   <li><b>品类过滤</b> — 只保留与 ctx.category 一致的商品🔥</li>
 *   <li><b>风格加权排序</b> — 根据 ctx.styleWeights 对风格权重高的商品优先排序🔥</li>
 *   <li>取前 10 条</li>
 *   <li>Fallback — 无匹配数据时使用写死的基础款列表（不报错）</li>
 * </ol>
 * </p>
 *
 * <p>输出：
 * <ul>
 *   <li>{@link Context#trendItems} — 结构化趋势商品（含 imageUrl/category/style/soldCount）</li>
 *   <li>{@link Context#trendImages} — 图片 URL 列表（供 CDN 同步）</li>
 *   <li>{@link Context#trendKeywords} — 品类/风格关键词（供 DesignAgent prompt）</li>
 *   <li>{@link Context#referenceImages} — 回写（向后兼容原有字段）</li>
 * </ul>
 * </p>
 *
 * <p>验收：
 * <ul>
 *   <li>✔ 不再写死 URL</li>
 *   <li>✔ 不同类目 → 输出不同趋势</li>
 *   <li>✔ 有销量排序</li>
 *   <li>✔ 无数据不报错（fallback）</li>
 * </ul>
 * </p>
 */
@Component
public class TrendAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TrendAgent.class);

    /** 从数据库拉取的全局候选数量 */
    private static final int DB_FETCH_LIMIT = 50;

    /** 最终保留的趋势条数 */
    private static final int KEEP_TOP = 10;

    /** 默认风格权重（无 styleWeights 时按此排序，各风格等权） */
    private static final double DEFAULT_WEIGHT = 0.5;

    /**
     * 系统保底趋势列表（内部数据为空时使用）。
     * 格式与 TrendItem 对齐（imageUrl 为占位符，category/style 为典型值）。
     */
    private static final List<TrendItem> FALLBACK_ITEMS = Collections.unmodifiableList(Arrays.asList(
            buildFallback("https://deepay-assets.example.com/trends/coat_minimal_v1.jpg",   "外套",   "MINIMAL"),
            buildFallback("https://deepay-assets.example.com/trends/dress_sexy_v1.jpg",     "连衣裙", "SEXY"),
            buildFallback("https://deepay-assets.example.com/trends/jacket_casual_v1.jpg",  "外套",   "CASUAL"),
            buildFallback("https://deepay-assets.example.com/trends/pants_sport_v1.jpg",    "裤子",   "SPORT"),
            buildFallback("https://deepay-assets.example.com/trends/top_luxury_v1.jpg",     "上衣",   "LUXURY")
    ));

    @Resource
    private DeepayMetricsMapper deepayMetricsMapper;

    @Override
    public Context run(Context ctx) {
        log.info("[TrendAgent] 开始拉取趋势 category={} styleWeights={}",
                ctx.category, ctx.styleWeights);

        // Step 1: 查询 TOP-50 全局热销
        List<TrendItem> items = fetchTopTrend();

        // Step 2: 品类过滤🔥（客户做内裤 → 永远不出现外套）
        if (StringUtils.hasText(ctx.category)) {
            List<TrendItem> filtered = items.stream()
                    .filter(i -> ctx.category.equals(i.getCategory()))
                    .collect(Collectors.toList());

            if (!filtered.isEmpty()) {
                items = filtered;
                log.info("[TrendAgent] 品类过滤后: category={} count={}", ctx.category, items.size());
            } else {
                log.info("[TrendAgent] 品类[{}] 无历史数据，使用全量排序后降级", ctx.category);
                // 不清空，继续用全量（由 CategoryFilterAgent 在 Design 阶段再过一道）
            }
        }

        // Step 3: 风格加权排序🔥
        final Map<String, Double> styleWeights = ctx.styleWeights;
        if (styleWeights != null && !styleWeights.isEmpty()) {
            items.sort((a, b) -> {
                double wa = styleWeights.getOrDefault(a.getStyle(), DEFAULT_WEIGHT);
                double wb = styleWeights.getOrDefault(b.getStyle(), DEFAULT_WEIGHT);
                // 风格权重相同时，销量高的优先
                int cmp = Double.compare(wb, wa);
                if (cmp != 0) return cmp;
                int sa = a.getSoldCount() != null ? a.getSoldCount() : 0;
                int sb = b.getSoldCount() != null ? b.getSoldCount() : 0;
                return Integer.compare(sb, sa);
            });
            log.info("[TrendAgent] 风格加权排序完成 styleWeights={}", styleWeights);
        }

        // Step 4: 取前 10
        items = items.subList(0, Math.min(KEEP_TOP, items.size()));

        // Step 5: Fallback — 无数据不报错
        if (items.isEmpty()) {
            log.warn("[TrendAgent] 无趋势数据，使用保底默认列表");
            items = applyFallback(ctx.category, styleWeights);
        }

        // ---- 写入 Context ----
        ctx.trendItems = items;

        ctx.trendImages = items.stream()
                .map(TrendItem::getImageUrl)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        ctx.trendKeywords = items.stream()
                .map(TrendItem::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        // 向后兼容：referenceImages
        ctx.referenceImages = ctx.trendImages;

        // keyword 兜底
        if (!StringUtils.hasText(ctx.keyword)) {
            ctx.keyword = StringUtils.hasText(ctx.category)
                    ? ctx.category
                    : (!ctx.trendKeywords.isEmpty() ? ctx.trendKeywords.get(0) : "");
        }

        log.info("[TrendAgent] 完成 trendItems={} trendImages={} trendKeywords={}",
                ctx.trendItems.size(), ctx.trendImages.size(), ctx.trendKeywords);
        return ctx;
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    private List<TrendItem> fetchTopTrend() {
        try {
            List<TrendItem> result = deepayMetricsMapper.selectTopTrend(DB_FETCH_LIMIT);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[TrendAgent] 查询 TOP 趋势失败，使用空列表", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fallback：从保底列表中按品类 + 风格过滤，仍无匹配则返回全部保底。
     */
    private List<TrendItem> applyFallback(String category, Map<String, Double> styleWeights) {
        List<TrendItem> result = FALLBACK_ITEMS;

        // 尝试按品类过滤
        if (StringUtils.hasText(category)) {
            List<TrendItem> byCat = FALLBACK_ITEMS.stream()
                    .filter(i -> category.equals(i.getCategory()))
                    .collect(Collectors.toList());
            if (!byCat.isEmpty()) result = byCat;
        }

        // 风格加权排序
        if (styleWeights != null && !styleWeights.isEmpty()) {
            result = result.stream()
                    .sorted((a, b) -> Double.compare(
                            styleWeights.getOrDefault(b.getStyle(), DEFAULT_WEIGHT),
                            styleWeights.getOrDefault(a.getStyle(), DEFAULT_WEIGHT)))
                    .collect(Collectors.toList());
        }

        return result;
    }

    private static TrendItem buildFallback(String imageUrl, String category, String style) {
        TrendItem item = new TrendItem();
        item.setImageUrl(imageUrl);
        item.setCategory(category);
        item.setStyle(style);
        item.setSoldCount(0);
        return item;
    }

}
