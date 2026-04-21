package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * PublishAgent — 写入 deepay_product，状态 SELLING。
 */
@Component
public class PublishAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PublishAgent.class);

    @Resource
    private DeepayProductMapper deepayProductMapper;

    @Override
    public Context run(Context ctx) {
        DeepayProductDO product = new DeepayProductDO();
        product.setChainCode(ctx.chainCode);
        product.setTitle(ctx.title);
        product.setPrice(ctx.price);
        product.setStatus("SELLING");
        product.setSoldCount(0);
        product.setStock(0);
        product.setCreatedAt(LocalDateTime.now());
        deepayProductMapper.insert(product);

        ctx.productId = String.valueOf(product.getId());
        ctx.published = true;
        log.info("PublishAgent: 商品已上架，chainCode={} productId={}", ctx.chainCode, ctx.productId);
        return ctx;
    }

}


