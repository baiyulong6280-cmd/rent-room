package cn.iocoder.yudao.module.deepay.agent;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 趋势探测 Agent（找爆款）。
 *
 * <p>职责：从用户的 prompt 中识别爆款趋势关键词，写入 {@link Context#trendKeyword}。</p>
 *
 * <p>MVP 策略：对比预置热门关键词列表，命中则采用，否则使用 prompt 本身作为关键词。</p>
 */
@Component
public class TrendAgent implements Agent {

    /** 预置热门趋势关键词（MVP 写死，后续可对接选品平台 API） */
    private static final List<String> HOT_KEYWORDS = Arrays.asList(
            "羊绒", "羊毛", "皮草", "极简", "通勤", "vintage", "oversize",
            "军绿", "格纹", "条纹", "波西米亚", "复古", "街头", "法式",
            "韩系", "小香风", "机能风", "工装", "卫衣", "针织"
    );

    @Override
    public Context run(Context ctx) {
        String prompt = ctx.prompt != null ? ctx.prompt : "";
        ctx.trendKeyword = extractKeyword(prompt);
        return ctx;
    }

    /**
     * 从 prompt 中提取与热榜匹配的关键词。
     * 若无命中，则截取 prompt 前 10 个字符作为关键词。
     */
    private String extractKeyword(String prompt) {
        for (String kw : HOT_KEYWORDS) {
            if (prompt.contains(kw)) {
                return kw;
            }
        }
        // 未命中热榜，以 prompt 前段作为关键词
        return prompt.length() > 10 ? prompt.substring(0, 10) : prompt;
    }

}
