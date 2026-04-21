package cn.iocoder.yudao.module.deepay.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.deepay.agent.AIDecisionAgent;
import cn.iocoder.yudao.module.deepay.agent.Context;
import cn.iocoder.yudao.module.deepay.agent.InventoryAgent;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayOrderDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayOrderMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 支付回调（Webhook）Controller — 独立路径，不影响主流程。
 *
 * <pre>
 * POST /deepay/callback/payment
 *   1. 校验订单存在且未支付（幂等）
 *   2. markPaid → deepay_order.status = PAID
 *   3. InventoryAgent.onPaid()：
 *        @Transactional 内部：
 *          deepay_inventory.stock--
 *          deepay_product.sold_count++, stock--   ← 只此一处，不重复
 *   4. deepay_metrics.sold_count++
 *   5. AIDecisionAgent 二次分析 → BOOST/STOP/REDESIGN（可循环）
 * </pre>
 */
@Tag(name = "Deepay - 支付回调")
@RestController
@RequestMapping("/deepay/callback")
@Validated
public class DeepayPaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(DeepayPaymentCallbackController.class);

    @Resource private DeepayOrderMapper   deepayOrderMapper;
    @Resource private DeepayProductMapper deepayProductMapper;
    @Resource private DeepayMetricsMapper deepayMetricsMapper;
    @Resource private InventoryAgent      inventoryAgent;
    @Resource private AIDecisionAgent     aiDecisionAgent;

    @PostMapping("/payment")
    @Operation(summary = "支付成功回调：order=PAID, sold_count++, stock--, 触发二次决策")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Object>> onPayment(@Valid @RequestBody PaymentCallbackReqVO req) {
        log.info("支付回调收到，paymentId={}", req.getPaymentId());

        // 1. 查订单（幂等校验）
        DeepayOrderDO order = deepayOrderMapper.selectByPaymentId(req.getPaymentId());
        if (order == null) {
            return CommonResult.error(404, "订单不存在: " + req.getPaymentId());
        }
        if ("PAID".equals(order.getStatus())) {
            return CommonResult.error(400, "订单已支付，请勿重复回调");
        }

        String chainCode = order.getChainCode();

        // 2. 标记订单 PAID（可追溯）
        deepayOrderMapper.markPaid(req.getPaymentId());

        // 3. 扣减库存 + sold_count++（@Transactional，原子执行，只此一处）
        //    InventoryAgent.onPaid() 内部会调 incrementSoldCount，不需要在这里再调
        int remaining = inventoryAgent.onPaid(chainCode);

        // 4. 更新 deepay_metrics sold_count（可追溯快照）
        deepayMetricsMapper.update(null, new LambdaUpdateWrapper<DeepayMetricsDO>()
                .eq(DeepayMetricsDO::getChainCode, chainCode)
                .setSql("sold_count = sold_count + 1"));

        // 5. 触发 AIDecisionAgent 二次分析（可循环：支付 → 重新决策 → BOOST/STOP/REDESIGN）
        DeepayProductDO product = deepayProductMapper.selectByChainCode(chainCode);
        Context reCtx = buildReviewContext(chainCode, product);
        aiDecisionAgent.run(reCtx);
        log.info("二次决策完成 chainCode={} action={} reason={}",
                chainCode, reCtx.action, reCtx.decisionReason);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("chainCode",      chainCode);
        resp.put("paymentId",      req.getPaymentId());
        resp.put("orderStatus",    "PAID");
        resp.put("remainingStock", Math.max(0, remaining));
        resp.put("action",         reCtx.action);
        resp.put("decisionReason", reCtx.decisionReason);
        return success(resp);
    }

    // ----------------------------------------------------------------
    // 构造二次决策 Context（用最新 product 数据，不重建 chainCode）
    // ----------------------------------------------------------------
    private Context buildReviewContext(String chainCode, DeepayProductDO product) {
        Context ctx = new Context();
        ctx.chainCode = chainCode;
        if (product != null) {
            ctx.keyword    = product.getTitle();
            ctx.price      = product.getPrice();
            ctx.soldCount  = product.getSoldCount();
            ctx.stock      = product.getStock();
            // 虚拟打分：soldCount > 5 → 高分 BOOST；否则中分
            String key = chainCode + "-recheck";
            ctx.designImages = Collections.singletonList(key);
            ctx.imageScores  = Collections.singletonMap(key,
                    product.getSoldCount() != null && product.getSoldCount() > 5 ? 90 : 70);
        }
        return ctx;
    }

    // ---- Request VO ----
    public static class PaymentCallbackReqVO {
        @NotBlank(message = "paymentId 不能为空")
        private String paymentId;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String p) { this.paymentId = p; }
    }

}

