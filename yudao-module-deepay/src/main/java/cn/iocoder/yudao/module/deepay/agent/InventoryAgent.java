package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayInventoryMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * InventoryAgent — 初始化库存，写入 deepay_inventory。
 */
@Component
public class InventoryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(InventoryAgent.class);

    @Resource
    private DeepayInventoryMapper deepayInventoryMapper;

    @Resource
    private DeepayProductMapper deepayProductMapper;

    @Override
    public Context run(Context ctx) {
        ctx.stock       = 10;
        ctx.lockedStock = 0;

        // 写入 deepay_inventory
        DeepayInventoryDO inv = new DeepayInventoryDO();
        inv.setChainCode(ctx.chainCode);
        inv.setStock(ctx.stock);
        inv.setLockedStock(ctx.lockedStock);
        inv.setCreatedAt(LocalDateTime.now());
        deepayInventoryMapper.insert(inv);

        // 同步更新 deepay_product.stock
        if (ctx.productId != null) {
            deepayProductMapper.addStock(Long.parseLong(ctx.productId), ctx.stock);
        }

        log.info("InventoryAgent: 库存初始化完成，chainCode={} stock={}", ctx.chainCode, ctx.stock);
        return ctx;
    }

}

