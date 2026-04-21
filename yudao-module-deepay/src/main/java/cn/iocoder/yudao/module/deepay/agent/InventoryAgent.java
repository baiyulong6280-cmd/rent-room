package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayInventoryMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * InventoryAgent — 库存管理。
 *
 * <p>职责：
 * <ul>
 *   <li><b>初始化</b>：新商品上架时 stock=10，lockedStock=0，写入 deepay_inventory，同步 deepay_product.stock。</li>
 *   <li><b>支付扣减</b>（由支付回调调用 {@link #onPaid}）：stock--、soldCount++，事务保证原子。</li>
 *   <li><b>低库存信号</b>：stock &lt; LOW_STOCK 时输出告警，由复盘调度器决策补货。</li>
 * </ul>
 * </p>
 */
@Component
public class InventoryAgent implements Agent {

    private static final Logger log        = LoggerFactory.getLogger(InventoryAgent.class);
    private static final int    INIT_STOCK = 10;
    private static final int    LOW_STOCK  = 3;

    @Resource private DeepayInventoryMapper deepayInventoryMapper;
    @Resource private DeepayProductMapper   deepayProductMapper;

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
     * 支付成功后扣减库存并同步 soldCount（由支付回调 Controller 调用）。
     *
     * <p>事务保证：inventory stock-- 与 product sold_count++ 同时成功或同时回滚。</p>
     *
     * @param chainCode 链码
     * @return 扣减后剩余库存
     * @throws RuntimeException chainCode 对应的库存或商品记录不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public int onPaid(String chainCode) {
        // ① 一次查，判空抛异常（不做静默跳过）
        DeepayInventoryDO inv = deepayInventoryMapper.selectByChainCode(chainCode);
        if (inv == null) {
            throw new RuntimeException("inventory not found: " + chainCode);
        }

        DeepayProductDO product = deepayProductMapper.selectByChainCode(chainCode);
        if (product == null) {
            throw new RuntimeException("product not found: " + chainCode);
        }

        // ② 扣减库存
        int remaining = Math.max(0, inv.getStock() - 1);
        int locked    = Math.max(0, inv.getLockedStock() - 1);

        deepayInventoryMapper.update(null, new LambdaUpdateWrapper<DeepayInventoryDO>()
                .eq(DeepayInventoryDO::getChainCode, chainCode)
                .set(DeepayInventoryDO::getStock, remaining)
                .set(DeepayInventoryDO::getLockedStock, locked));

        // ③ sold_count++，stock--（原子 SQL）
        deepayProductMapper.incrementSoldCount(product.getId());

        if (remaining < LOW_STOCK) {
            log.warn("InventoryAgent: 库存告警 chainCode={} stock={}，建议补货", chainCode, remaining);
        }

        log.info("InventoryAgent.onPaid: chainCode={} 剩余库存={}", chainCode, remaining);
        return remaining;
    }

}



