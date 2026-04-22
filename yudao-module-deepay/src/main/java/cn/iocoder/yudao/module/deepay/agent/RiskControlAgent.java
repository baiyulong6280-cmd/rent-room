package cn.iocoder.yudao.module.deepay.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * RiskControlAgent — 过滤 IP / 版权风险关键词（Phase 8）。
 *
 * <p>检测 finalPrompt 和 designDetails 是否包含被封禁品牌词；
 * riskScore = matches * 30（上限 100）。riskScore &gt; 80 时清除 finalPrompt 并改为安全通用描述。
 * 始终在 finalPrompt 末尾追加 ", no brand logo, no trademark"。</p>
 */
@Component
public class RiskControlAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(RiskControlAgent.class);

    private static final List<String> BLOCKED_BRANDS = Arrays.asList(
            "nike", "adidas", "gucci", "lv", "louis vuitton", "chanel",
            "prada", "dior", "hermes", "zara", "h&m"
    );

    /** 每个命中的违禁品牌词对 riskScore 的贡献（30 分 × 命中数，上限 100） */
    private static final int RISK_SCORE_PER_MATCH = 30;
    private static final int HIGH_RISK_THRESHOLD  = 80;
            "minimalist fashion clothing, original design, no brand logo, no trademark";

    @Override
    public Context run(Context ctx) {
        try {
            int matches = countBlockedMatches(ctx.finalPrompt) + countBlockedMatches(ctx.designDetails);
            int riskScore = Math.min(100, matches * RISK_SCORE_PER_MATCH);
            ctx.riskScore = riskScore;

            if (riskScore > HIGH_RISK_THRESHOLD) {
                log.warn("[RiskControlAgent] 高风险 riskScore={} finalPrompt 已重置为安全描述", riskScore);
                ctx.finalPrompt = SAFE_PROMPT;
            } else {
                if (StringUtils.hasText(ctx.finalPrompt)) {
                    ctx.finalPrompt = ctx.finalPrompt + ", no brand logo, no trademark";
                } else {
                    ctx.finalPrompt = SAFE_PROMPT;
                }
            }

            log.info("[RiskControlAgent] riskScore={} finalPrompt={}", riskScore, ctx.finalPrompt);
        } catch (Exception e) {
            log.warn("[RiskControlAgent] 风险检测异常，跳过", e);
        }
        return ctx;
    }

    private int countBlockedMatches(String text) {
        if (!StringUtils.hasText(text)) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String brand : BLOCKED_BRANDS) {
            if (lower.contains(brand)) {
                count++;
            }
        }
        return count;
    }

}
