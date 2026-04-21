package cn.iocoder.yudao.module.deepay.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.deepay.agent.AIDecisionAgent;
import cn.iocoder.yudao.module.deepay.agent.Context;
import cn.iocoder.yudao.module.deepay.agent.InventoryAgent;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayOrderDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayOrderMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayMetricsMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 支付回调（Webhook）Controller — 独立路径，不影响主流程。
 *
 * <pre>
 * POST /deepay/callback/payment
 *   1. 更新 deepay_order.status = PAID
 *   2. 更新 deepay_product.status = SOLD（sold_count++，stock--）
 *   3. 扣减 deepay_inventory（库存不足则记录低库存信号）
 *   4. 触发 AIDecisionAgent 二次分析（可循环）
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
    @Operation(summary = "支付成功回调：order=PAID, product=SOLD, soldCount++, stock--, 触发二次决策")
    public CommonResult<Map<String, Object>> onPayment(@Valid @RequestBody PaymentCallbackReqVO req) {
        log.info("支付回调收到，paymentId={}", req.getPaymentId());

        // 1. 查订单
        DeepayOrderDO order = deepayOrderMapper.selectByPaymentId(req.getPaymentId());
        if (order == null) {
            return CommonResult.error(404, "订单不存在: " + req.getPaymentId());
        }
        if ("PAID".equals(order.getStatus())) {
            return CommonResult.error(400, "订单已支付，请勿重复回调");
        }

        String chainCode = order.getChainCode();

        // 2. 更新订单状态 → PAID（可追溯）
        deepayOrderMapper.markPaid(req.getPaymentId());

        // 3. 更新商品状态 → SOLD（可追溯）
        DeepayProductDO product = deepayProductMapper.selectByChainCode(chainCode);
        if (product != null) {
            deepayProductMapper.incrementSoldCount(product.getId());
        }

        // 4. 扣减库存（库存<3 时内部记录补货信号）
        int remaining = inventoryAgent.onPaid(chainCode);

        // 5. 更新 deepay_metrics sold_count（可追溯）
        deepayMetricsMapper.update(null, new LambdaUpdateWrapper<>()
                .eq(cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO::getChainCode, chainCode)
                .setSql("sold_count = sold_count + 1"));

        // 6. 触发 AIDecisionAgent 二次分析（可循环：支付 → 重新决策 → BOOST/STOP/REDESIGN）
        Context reCtx = buildReviewContext(chainCode, product);
        aiDecisionAgent.run(reCtx);
        log.info("支付回调：二次决策完成，chainCode={} action={} reason={}", chainCode, reCtx.action, reCtx.decisionReason);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("chainCode",     chainCode);
        resp.put("paymentId",     req.getPaymentId());
        resp.put("orderStatus",   "PAID");
        resp.put("remainingStock", Math.max(0, remaining));
        resp.put("action",        reCtx.action);
        resp.put("decisionReason", reCtx.decisionReason);
        return success(resp);
    }

    private Context buildReviewContext(String chainCode, DeepayProductDO product) {
        Context ctx = new Context();
        ctx.chainCode = chainCode;
        if (product != null) {
            ctx.keyword     = product.getTitle();
            ctx.price       = product.getPrice();
            ctx.soldCount   = product.getSoldCount();
            ctx.stock       = product.getStock();
            // 构造一张虚拟 designImages 供 AIDecisionAgent 走评分分支
            ctx.designImages = java.util.Collections.singletonList(
                    product.getChainCode() + "-recheck");
            ctx.imageScores = java.util.Collections.singletonMap(
                    product.getChainCode() + "-recheck",
                    product.getSoldCount() != null && product.getSoldCount() > 5 ? 90 : 70);
        }
        return ctx;
    }

    // ---- Request VO ----
    public static class PaymentCallbackReqVO {
        @NotBlank(message = "paymentId 不能为空")
        private String paymentId;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    }

}
