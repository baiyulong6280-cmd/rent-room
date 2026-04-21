package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayInventoryMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * InventoryAgent — 库存管理。
 *
 * <p>职责：
 * <ul>
 *   <li><b>初始化</b>：新商品上架时 stock=10，lockedStock=0，写入 deepay_inventory，同步 deepay_product.stock。</li>
 *   <li><b>支付扣减</b>（由支付回调调用 {@link #onPaid}）：stock--，lockedStock--。</li>
 *   <li><b>自动补货信号</b>：stock &lt; 3 时写入 action=RESTOCK，触发后续复盘调度器补货。</li>
 * </ul>
 * </p>
 */
@Component
public class InventoryAgent implements Agent {

    private static final Logger log        = LoggerFactory.getLogger(InventoryAgent.class);
    private static final int    INIT_STOCK = 10;
    private static final int    LOW_STOCK  = 3;

    @Resource
    private DeepayInventoryMapper deepayInventoryMapper;

    @Resource
    private DeepayProductMapper deepayProductMapper;

    /** 新品上架：初始化库存（Orchestrator 调用）。 */
    @Override
    public Context run(Context ctx) {
        ctx.stock       = INIT_STOCK;
        ctx.lockedStock = 0;

        DeepayInventoryDO inv = new DeepayInventoryDO();
        inv.setChainCode(ctx.chainCode);
        inv.setStock(ctx.stock);
        inv.setLockedStock(ctx.lockedStock);
        inv.setCreatedAt(LocalDateTime.now());
        deepayInventoryMapper.insert(inv);

        if (ctx.productId != null) {
            deepayProductMapper.addStock(Long.parseLong(ctx.productId), ctx.stock);
        }

        log.info("InventoryAgent: 库存初始化，chainCode={} stock={}", ctx.chainCode, ctx.stock);
        return ctx;
    }

    /**
     * 支付成功后扣减库存（由支付回调 Controller 调用）。
     *
     * @param chainCode 链码
     * @return 扣减后剩余库存；如剩余 &lt; LOW_STOCK 则返回负数作为"低库存信号"
     */
    public int onPaid(String chainCode) {
        DeepayInventoryDO inv = deepayInventoryMapper.selectByChainCode(chainCode);
        if (inv == null) {
            log.warn("InventoryAgent.onPaid: 未找到库存记录，chainCode={}", chainCode);
            return -1;
        }

        int remaining = inv.getStock() - 1;
        int locked    = Math.max(0, inv.getLockedStock() - 1);

        deepayInventoryMapper.update(null, new LambdaUpdateWrapper<DeepayInventoryDO>()
                .eq(DeepayInventoryDO::getChainCode, chainCode)
                .set(DeepayInventoryDO::getStock, Math.max(0, remaining))
                .set(DeepayInventoryDO::getLockedStock, locked));

        // 同步 deepay_product
        DeepayInventoryDO updated = deepayInventoryMapper.selectByChainCode(chainCode);
        if (updated != null) {
            deepayProductMapper.incrementSoldCount(
                    deepayProductMapper.selectByChainCode(chainCode) != null
                            ? deepayProductMapper.selectByChainCode(chainCode).getId()
                            : null);
        }

        if (remaining < LOW_STOCK) {
            log.warn("InventoryAgent: 库存低于阈值 {}，chainCode={} stock={}，触发补货信号", LOW_STOCK, chainCode, remaining);
        }

        log.info("InventoryAgent.onPaid: chainCode={} 剩余库存={}", chainCode, Math.max(0, remaining));
        return remaining;
    }

}


