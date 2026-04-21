package cn.iocoder.yudao.module.deepay.agent;

/**
 * 收款 Agent（PaymentAgent）。
 *
 * <p>职责：为已上架的商品生成 Jeepay 和 Swan 双通道收款链接，
 * 分别写入 {@link Context#jeepayLink} 和 {@link Context#swanLink}。</p>
 *
 * <p>MVP 策略：生成规范格式的 mock 收款链接，包含链码和价格信息。
 * 后续接入真实 Jeepay SDK / Swan API 时只需替换本类。</p>
 *
 * <p>同时保留 {@link Context#iban} 字段兼容旧 {@link FinanceAgent} 调用方。</p>
 */
public class PaymentAgent implements Agent {

    private static final String JEEPAY_BASE = "https://pay.jeepay.com/order/";
    private static final String SWAN_BASE   = "https://pay.swan.io/checkout/";

    @Override
    public Context run(Context ctx) {
        String code  = ctx.chainCode != null ? ctx.chainCode : "UNKNOWN";
        int    price = ctx.price     != null ? ctx.price      : 0;

        // Jeepay 收款链接：格式 {base}{chainCode}?amount={price_fen}
        ctx.jeepayLink = JEEPAY_BASE + code + "?amount=" + price;

        // Swan 收款链接：格式 {base}{chainCode}?currency=CNY&amount={price_fen}
        ctx.swanLink   = SWAN_BASE   + code + "?currency=CNY&amount=" + price;

        // 兼容旧 iban 字段
        ctx.iban = "DEEPAY-" + code;

        return ctx;
    }

}
