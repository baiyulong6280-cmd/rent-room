package cn.iocoder.yudao.module.deepay.scheduler;

import cn.iocoder.yudao.module.deepay.agent.Context;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayInventoryMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import cn.iocoder.yudao.module.deepay.orchestrator.ProductionOrchestrator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动复盘调度器 — 系统智能来源（闭环核心）。
 *
 * <p>每小时扫描所有在售商品，根据销售量自动决策：
 * <pre>
 *   soldCount ≥ 8          → BOOST    （继续推广，低库存时自动补货）
 *   soldCount ≤ 2          → STOP     （下架，停止运营）
 *   2 &lt; soldCount &lt; 8 → REDESIGN （改款，触发全新生产流水线）
 * </pre>
 * 每次决策结果写入 deepay_metrics（可追溯）。
 * REDESIGN 分支调用 {@link ProductionOrchestrator#run(Context)} 从头重跑（可循环）。
 * </p>
 */
@Component
public class DeepayReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeepayReviewScheduler.class);

    private static final int BOOST_THRESHOLD     = 8;
    private static final int STOP_THRESHOLD      = 2;
    private static final int LOW_STOCK_THRESHOLD = 3;
    private static final int RESTOCK_DELTA       = 10;

    @Resource private DeepayProductMapper    deepayProductMapper;
    @Resource private DeepayMetricsMapper    deepayMetricsMapper;
    @Resource private DeepayInventoryMapper  deepayInventoryMapper;
    @Resource private ProductionOrchestrator productionOrchestrator;

    /** 每小时整点执行一次复盘。 */
    @Scheduled(cron = "0 0 * * * *")
    public void review() {
        log.info("=== 自动复盘开始 ===");

        List<DeepayProductDO> sellingProducts = deepayProductMapper.selectBySelling();
        if (sellingProducts.isEmpty()) {
            log.info("自动复盘：当前无在售商品，跳过");
            return;
        }

        for (DeepayProductDO product : sellingProducts) {
            try {
                reviewOne(product);
            } catch (Exception e) {
                log.error("自动复盘：处理异常 chainCode={}", product.getChainCode(), e);
            }
        }

        log.info("=== 自动复盘完成，共处理 {} 件商品 ===", sellingProducts.size());
    }

    // ----------------------------------------------------------------

    private void reviewOne(DeepayProductDO product) {
        String chainCode = product.getChainCode();
        int soldCount    = product.getSoldCount() != null ? product.getSoldCount() : 0;

        String action;
        String reason;

        if (soldCount >= BOOST_THRESHOLD) {
            action = "BOOST";
            reason = "soldCount=" + soldCount + " ≥ " + BOOST_THRESHOLD + "，卖得快，继续推广";
            doBoost(product);
        } else if (soldCount <= STOP_THRESHOLD) {
            action = "STOP";
            reason = "soldCount=" + soldCount + " ≤ " + STOP_THRESHOLD + "，滞销，执行下架";
            doStop(product);
        } else {
            action = "REDESIGN";
            reason = "soldCount=" + soldCount + "，表现一般，触发改款重新生产";
            doRedesign(product);
        }

        // 写入复盘快照（可追溯，每条记录独立保留）
        DeepayMetricsDO snapshot = new DeepayMetricsDO();
        snapshot.setChainCode(chainCode);
        snapshot.setSoldCount(soldCount);
        snapshot.setPrice(product.getPrice());
        snapshot.setCategory("REVIEW:" + action + " | " + reason);
        snapshot.setCreatedAt(LocalDateTime.now());
        deepayMetricsMapper.insert(snapshot);

        log.info("[复盘] chainCode={} action={} reason={}", chainCode, action, reason);
    }

    /** BOOST — 库存不足时自动补货，状态保持 SELLING */
    private void doBoost(DeepayProductDO product) {
        DeepayInventoryDO inv = deepayInventoryMapper.selectByChainCode(product.getChainCode());
        if (inv != null && inv.getStock() < LOW_STOCK_THRESHOLD) {
            deepayInventoryMapper.update(null, new LambdaUpdateWrapper<DeepayInventoryDO>()
                    .eq(DeepayInventoryDO::getChainCode, product.getChainCode())
                    .setSql("stock = stock + " + RESTOCK_DELTA));
            deepayProductMapper.addStock(product.getId(), RESTOCK_DELTA);
            log.info("[BOOST] 自动补货 chainCode={} +{}", product.getChainCode(), RESTOCK_DELTA);
        }
        deepayProductMapper.updateStatus(product.getId(), "SELLING");
    }

    /** STOP — 下架商品 */
    private void doStop(DeepayProductDO product) {
        deepayProductMapper.updateStatus(product.getId(), "STOPPED");
        log.info("[STOP] 商品已下架 chainCode={}", product.getChainCode());
    }

    /**
     * REDESIGN — 将当前商品标记为 REDESIGNING，然后用原始 keyword
     * 触发完整生产流水线，生成新的 chainCode 商品（卖 → 再做 闭环）。
     */
    private void doRedesign(DeepayProductDO product) {
        deepayProductMapper.updateStatus(product.getId(), "REDESIGNING");

        // 还原原始 keyword（去除 ProductAgent 自动加的后缀）
        String keyword = product.getTitle() != null ? product.getTitle() : "新款";
        if (keyword.endsWith(" 限量款")) {
            keyword = keyword.substring(0, keyword.length() - 4);
        }

        log.info("[REDESIGN] 触发重新生产 chainCode={} keyword={}", product.getChainCode(), keyword);

        Context ctx = new Context();
        ctx.keyword = keyword;
        productionOrchestrator.run(ctx);

        log.info("[REDESIGN] 新品已完成 新chainCode={} url={}", ctx.chainCode, ctx.productUrl);
    }

}
