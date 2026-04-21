package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * PricingAgent — 确定最终售价并更新 deepay_product.price。
 *
 * <p>优先级：suggestPrice（AIDecisionAgent 输出）> 默认 299。</p>
 * <p>后续可接入动态定价模型（趋势因子 × 基础价）。</p>
 */
@Component
public class PricingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PricingAgent.class);

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("299");

    @Resource
    private DeepayProductMapper deepayProductMapper;

    @Override
    public Context run(Context ctx) {
        ctx.price = (ctx.suggestPrice != null) ? ctx.suggestPrice : DEFAULT_PRICE;

        if (ctx.productId != null) {
            deepayProductMapper.updatePrice(Long.parseLong(ctx.productId), ctx.price);
        }

        log.info("PricingAgent: 定价完成，price={} productId={}", ctx.price, ctx.productId);
        return ctx;
    }

}


