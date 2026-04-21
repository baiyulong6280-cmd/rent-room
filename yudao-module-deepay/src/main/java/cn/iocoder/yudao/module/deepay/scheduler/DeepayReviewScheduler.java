package cn.iocoder.yudao.module.deepay.scheduler;

import cn.iocoder.yudao.module.deepay.agent.Context;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayInventoryMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import cn.iocoder.yudao.module.deepay.orchestrator.ProductionOrchestrator;
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
 *   soldCount ≥ BOOST 阈值  → BOOST    （继续推广，低库存时自动补货）
 *   soldCount ≤ STOP  阈值  → STOP     （下架，停止运营）
 *   其他                    → REDESIGN （改款，触发全新生产流水线）
 * </pre>
 * 阈值由历史销售均值 ± 标准差动态计算，无历史数据时 fallback 到静态值 BOOST=8 / STOP=2。
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
    @Resource private DeepayStyleChainMapper deepayStyleChainMapper;
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

        // 动态阈值：均值 ± 标准差（无历史数据时 fallback 到静态常量）
        int boostThreshold = BOOST_THRESHOLD;
        int stopThreshold  = STOP_THRESHOLD;
        try {
            Double mean   = deepayMetricsMapper.selectAvgSoldCount();
            Double stddev = deepayMetricsMapper.selectStddevSoldCount();
            if (mean != null && stddev != null) {
                boostThreshold = Math.max(1, (int) Math.round(mean + stddev));
                stopThreshold  = Math.max(0, (int) Math.round(mean - stddev));
                log.info("自动复盘：动态阈值 — 均值={} 标准差={} BOOST>={} STOP<={}",
                        String.format("%.2f", mean), String.format("%.2f", stddev),
                        boostThreshold, stopThreshold);
            } else {
                log.info("自动复盘：暂无历史销售数据，使用静态阈值 BOOST>={} STOP<={}",
                        boostThreshold, stopThreshold);
            }
        } catch (Exception e) {
            log.warn("自动复盘：计算动态阈值失败，使用静态阈值", e);
        }

        for (DeepayProductDO product : sellingProducts) {
            try {
                reviewOne(product, boostThreshold, stopThreshold);
            } catch (Exception e) {
                log.error("自动复盘：处理异常 chainCode={}", product.getChainCode(), e);
            }
        }

        log.info("=== 自动复盘完成，共处理 {} 件商品 ===", sellingProducts.size());
    }

    // ----------------------------------------------------------------

    private void reviewOne(DeepayProductDO product, int boostThreshold, int stopThreshold) {
        String chainCode = product.getChainCode();
        int soldCount    = product.getSoldCount() != null ? product.getSoldCount() : 0;

        String action;
        String reason;

        if (soldCount >= boostThreshold) {
            action = "BOOST";
            reason = "soldCount=" + soldCount + " ≥ " + boostThreshold + "，卖得快，继续推广";
            doBoost(product);
        } else if (soldCount <= stopThreshold) {
            action = "STOP";
            reason = "soldCount=" + soldCount + " ≤ " + stopThreshold + "，滞销，执行下架";
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

        // 优先从 deepay_style_chain 取原始 keyword（ChainAgent 落库时记录，与 title 格式无关）
        String keyword = null;
        if (product.getChainCode() != null) {
            DeepayStyleChainDO chain = deepayStyleChainMapper.selectByChainCode(product.getChainCode());
            if (chain != null && chain.getKeyword() != null) {
                keyword = chain.getKeyword();
            }
        }
        // 兜底：直接使用商品标题（keyword 信息缺失时可接受的降级）
        if (keyword == null) {
            keyword = product.getTitle() != null ? product.getTitle() : "新款";
        }

        log.info("[REDESIGN] 触发重新生产 chainCode={} keyword={}", product.getChainCode(), keyword);

        Context ctx = new Context();
        ctx.keyword = keyword;
        productionOrchestrator.run(ctx);

        log.info("[REDESIGN] 新品已完成 新chainCode={} url={}", ctx.chainCode, ctx.productUrl);
    }

}
