package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayVariantDO;
import cn.iocoder.yudao.module.deepay.service.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DesignVariantAgent — 基于 Top 图片生成 5 个颜色 / 面料变体（Phase 8）。
 *
 * <p>从 {@link Context#topImages}（或 {@link Context#designImages}）取第一张图作为基准，
 * 为每个颜色 × 面料组合调用 FluxService 生成 1 张变体图，并将结果写入 {@link Context#variants}。</p>
 */
@Component
public class DesignVariantAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DesignVariantAgent.class);

    private static final String[] COLORS  = {"黑色", "白色", "灰色", "米白", "卡其"};
    private static final String[] FABRICS = {"棉",   "牛仔", "针织", "羊毛", "丝绸"};

    @Resource
    private FluxService fluxService;

    @Override
    public Context run(Context ctx) {
        try {
            String basePrompt = StringUtils.hasText(ctx.finalPrompt) ? ctx.finalPrompt : ctx.stylePrompt;
            if (!StringUtils.hasText(basePrompt)) {
                log.info("[DesignVariantAgent] 无 finalPrompt/stylePrompt，跳过变体生成");
                return ctx;
            }

            List<DeepayVariantDO> variants = new ArrayList<>();
            String chainCode = ctx.chainCode != null ? ctx.chainCode : "UNKNOWN";

            for (int i = 0; i < 5; i++) {
                String color  = COLORS[i];
                String fabric = FABRICS[i];

                String variantPrompt = basePrompt + ", " + color + ", " + fabric + " fabric variant";
                List<String> generated = fluxService.generateImages(variantPrompt, 1);
                String imageUrl = (generated != null && !generated.isEmpty()) ? generated.get(0) : "";

                DeepayVariantDO variant = new DeepayVariantDO();
                variant.setParentChainCode(chainCode);
                variant.setVariantCode(chainCode + "-V" + String.format("%03d", i + 1));
                variant.setCategory(ctx.category);
                variant.setColor(color);
                variant.setFabric(fabric);
                variant.setStyle(ctx.style);
                variant.setImageUrl(imageUrl);
                variant.setDesignPrompt(variantPrompt);
                variant.setCreatedAt(LocalDateTime.now());

                variants.add(variant);
                log.info("[DesignVariantAgent] 变体[{}] color={} fabric={} url={}", i + 1, color, fabric, imageUrl);
            }

            ctx.variants = variants;
            log.info("[DesignVariantAgent] DONE 生成 {} 个变体 chainCode={}", variants.size(), chainCode);
        } catch (Exception e) {
            log.warn("[DesignVariantAgent] 变体生成异常，跳过", e);
        }
        return ctx;
    }

}
