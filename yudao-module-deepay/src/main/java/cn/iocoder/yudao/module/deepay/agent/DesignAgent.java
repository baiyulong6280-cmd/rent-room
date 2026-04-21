package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.service.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DesignAgent — 根据客户画像生成个性化改款图（Phase 6 升级版）。
 *
 * <p>升级内容（相比原版）：
 * <ul>
 *   <li>Prompt 注入品类、风格、市场、人群、趋势图关键词，生成的图天然贴合客户行业</li>
 *   <li>输入参数：category / style / market / targetAge / gender / trendImages</li>
 *   <li>保留 keyword 兜底（无画像时可继续正常运行）</li>
 * </ul>
 * </p>
 *
 * <p>Prompt 格式：
 * <pre>
 *   设计一款 {category}，
 *   风格={style}，
 *   市场={market}，
 *   人群={targetAge} {gender}，
 *   参考热销风格={trendKeywords}
 * </pre>
 * </p>
 */
@Component
public class DesignAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DesignAgent.class);

    @Resource
    private FluxService fluxService;

    @Override
    public Context run(Context ctx) {
        String prompt = buildPrompt(ctx);
        log.info("[DesignAgent] 生成设计图 prompt={}", prompt);
        ctx.designImages = fluxService.generateImages(prompt);
        return ctx;
    }

    // ====================================================================
    // Prompt 构建（核心升级点）
    // ====================================================================

    /**
     * 根据 Context 中的客户画像字段拼接结构化 prompt。
     *
     * <p>有画像时生成精准 prompt；无画像时退化为仅用 keyword，行为与原版完全相同。</p>
     */
    private String buildPrompt(Context ctx) {
        // 画像维度
        String category   = StringUtils.hasText(ctx.category)   ? ctx.category   : ctx.keyword;
        String style      = StringUtils.hasText(ctx.style)       ? ctx.style       : null;
        String market     = StringUtils.hasText(ctx.market)      ? ctx.market      : null;
        String targetAge  = StringUtils.hasText(ctx.targetAge)   ? ctx.targetAge   : null;
        String gender     = StringUtils.hasText(ctx.gender)      ? ctx.gender      : null;
        String trendRef   = buildTrendReference(ctx);

        // 无任何画像维度 → 退化为原始关键词模式
        if (style == null && market == null && targetAge == null && gender == null
                && trendRef == null) {
            String kw = StringUtils.hasText(category) ? category : "";
            log.debug("[DesignAgent] 无画像维度，退化为关键词模式 keyword={}", kw);
            return kw;
        }

        // 结构化 prompt
        StringBuilder sb = new StringBuilder();
        sb.append("设计一款");
        if (StringUtils.hasText(category))  sb.append(" ").append(category);
        if (StringUtils.hasText(style))     sb.append("，风格=").append(style);
        if (StringUtils.hasText(market))    sb.append("，市场=").append(marketLabel(market));
        if (targetAge != null || gender != null) {
            sb.append("，人群=");
            if (targetAge != null) sb.append(ageLabel(targetAge));
            if (gender    != null) sb.append(" ").append(genderLabel(gender));
        }
        if (StringUtils.hasText(trendRef))  sb.append("，参考热销风格=").append(trendRef);

        return sb.toString().trim();
    }

    /** 将趋势关键词拼接成一句话参考描述，最多取前 3 个。 */
    private String buildTrendReference(Context ctx) {
        // 优先使用 trendKeywords
        if (ctx.trendKeywords != null && !ctx.trendKeywords.isEmpty()) {
            List<String> keywords = ctx.trendKeywords.stream()
                    .filter(StringUtils::hasText)
                    .limit(3)
                    .collect(Collectors.toList());
            if (!keywords.isEmpty()) {
                return String.join("、", keywords);
            }
        }
        // 无趋势关键词
        return null;
    }

    // ---- 标签翻译（供 FluxService 的英文 API 使用时，可扩展为英文映射）----

    private String marketLabel(String market) {
        switch (market) {
            case "EU": return "欧洲（EU）";
            case "US": return "北美（US）";
            case "ME": return "中东（ME）";
            default:   return "国内（CN）";
        }
    }

    private String ageLabel(String targetAge) {
        switch (targetAge) {
            case "MIDDLE": return "中年";
            case "ELDER":  return "中老年";
            default:       return "年轻";
        }
    }

    private String genderLabel(String gender) {
        switch (gender) {
            case "MALE":   return "男装";
            case "FEMALE": return "女装";
            default:       return "男女通用";
        }
    }

}
