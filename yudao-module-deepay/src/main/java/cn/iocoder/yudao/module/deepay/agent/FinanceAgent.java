package cn.iocoder.yudao.module.deepay.agent;

/**
 * 支付 Agent（MVP 使用模拟数据）。
 *
 * <p>职责：为本次商品生成收款 IBAN，写入 {@link Context#iban}。</p>
 *
 * <p>MVP 策略：生成形如 {@code DEEPAY-DEMO-XXXXXX} 的 mock IBAN 字符串。
 * 后续接入 Swan 支付或真实银行通道时只需替换本类。</p>
 */
public class FinanceAgent implements Agent {

    private static final String IBAN_PREFIX = "DEEPAY-DEMO-";

    @Override
    public Context run(Context ctx) {
        // MVP：用 chainCode 作为末段，生成可读的 mock IBAN
        ctx.iban = IBAN_PREFIX + (ctx.chainCode != null ? ctx.chainCode : "000000");
        return ctx;
    }

}
