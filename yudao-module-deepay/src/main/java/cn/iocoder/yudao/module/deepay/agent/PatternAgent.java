package cn.iocoder.yudao.module.deepay.agent;

/**
 * 打版 Agent（PatternAgent）。
 *
 * <p>职责：根据选中的设计图和链码，生成打版编码写入 {@link Context#patternCode}。</p>
 *
 * <p>打版编码格式：{@code PAT-{chainCode}-{样式序号}}，
 * 样式序号由 selectedImage URL 的哈希值决定，保证同款图片每次生成一致的版型编号。</p>
 *
 * <p>MVP 阶段本 Agent 仅生成编码标识，不调用外部打版系统。
 * 后续接入自动打版 CAD 系统时只需替换本类。</p>
 */
public class PatternAgent implements Agent {

    /** 可用样式序号数量（A-Z，共 26 种） */
    private static final int STYLE_COUNT = 26;

    @Override
    public Context run(Context ctx) {
        if (ctx.chainCode == null) {
            throw new IllegalStateException("PatternAgent: chainCode 为空，无法生成打版编码");
        }

        String styleSuffix = resolveStyleSuffix(ctx.selectedImage);
        ctx.patternCode = "PAT-" + ctx.chainCode + "-" + styleSuffix;
        return ctx;
    }

    /**
     * 根据图片 URL 哈希确定样式后缀（A-Z）。
     */
    private String resolveStyleSuffix(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return "A";
        }
        int index = Math.abs(imageUrl.hashCode()) % STYLE_COUNT;
        return String.valueOf((char) ('A' + index));
    }

}
