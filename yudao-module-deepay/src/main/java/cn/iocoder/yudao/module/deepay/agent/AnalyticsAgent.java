package cn.iocoder.yudao.module.deepay.agent;

/**
 * 复盘优化 Agent（AnalyticsAgent）🔥
 *
 * <p>职责：在整条流水线结束后汇总执行结果，生成复盘摘要报告，
 * 写入 {@link Context#analyticsReport}。</p>
 *
 * <p>MVP 输出：结构化文本报告，包含关键指标（关键词、定价、库存、链接等）。
 * 后续可扩展为：异步写入数据仓库、触发 BI 看板更新、推送钉钉 / 企微通知等。</p>
 */
public class AnalyticsAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        ctx.analyticsReport = buildReport(ctx);
        return ctx;
    }

    private String buildReport(Context ctx) {
        String priceCny = ctx.price != null
                ? String.format("%.2f 元", ctx.price / 100.0)
                : "未定价";

        return "===== Deepay 流水线复盘报告 =====" +
               "\n链码       : " + nvl(ctx.chainCode) +
               "\n爆款关键词 : " + nvl(ctx.trendKeyword) +
               "\n打版编码   : " + nvl(ctx.patternCode) +
               "\n选中图片   : " + nvl(ctx.selectedImage) +
               "\n定价       : " + priceCny +
               "\n初始库存   : " + nvl(ctx.initialStock) +
               "\n上架状态   : " + nvl(ctx.publishStatus) +
               "\nJeepay链接 : " + nvl(ctx.jeepayLink) +
               "\nSwan链接   : " + nvl(ctx.swanLink) +
               "\n商品链接   : https://deepay.link/" + nvl(ctx.chainCode) +
               "\n=================================";
    }

    private String nvl(Object val) {
        return val != null ? val.toString() : "-";
    }

}
