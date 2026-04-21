package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 库存智能 Agent（InventoryAgent）。
 *
 * <p>职责：为新上架商品设定初始库存，并将库存数量、定价、收款链接批量写回数据库，
 * 最终将初始库存写入 {@link Context#initialStock}。</p>
 *
 * <p>MVP 库存策略：
 * <ul>
 *   <li>默认初始库存：50 件</li>
 *   <li>高端材质（价格 ≥ 400 元）：初始库存调低为 20 件（限量感）</li>
 * </ul>
 * </p>
 */
@Component
public class InventoryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(InventoryAgent.class);

    /** 普通款初始库存 */
    private static final int DEFAULT_STOCK = 50;
    /** 高端款初始库存（限量） */
    private static final int PREMIUM_STOCK = 20;
    /** 高端款价格门槛（分） */
    private static final int PREMIUM_PRICE_THRESHOLD = 40000;

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Override
    public Context run(Context ctx) {
        if (ctx.chainCode == null) {
            throw new IllegalStateException("InventoryAgent: chainCode 为空，无法设置库存");
        }

        // 根据定价判断库存策略
        int stock = DEFAULT_STOCK;
        if (ctx.price != null && ctx.price >= PREMIUM_PRICE_THRESHOLD) {
            stock = PREMIUM_STOCK;
        }
        ctx.initialStock = stock;

        // 将定价、支付链接、库存、状态批量写回 DB
        String paymentLink = buildPaymentLink(ctx);
        deepayStyleChainMapper.updateProductionFields(
                ctx.chainCode,
                ctx.price,
                paymentLink,
                stock,
                ctx.publishStatus != null ? ctx.publishStatus : "PUBLISHED"
        );

        log.info("InventoryAgent: 库存设置完成，chainCode={} stock={}", ctx.chainCode, stock);
        return ctx;
    }

    /**
     * 将 Jeepay 和 Swan 链接合并为存储格式：{jeepay}|{swan}。
     */
    private String buildPaymentLink(Context ctx) {
        String jeepay = ctx.jeepayLink != null ? ctx.jeepayLink : "";
        String swan   = ctx.swanLink   != null ? ctx.swanLink   : "";
        return jeepay + "|" + swan;
    }

}
