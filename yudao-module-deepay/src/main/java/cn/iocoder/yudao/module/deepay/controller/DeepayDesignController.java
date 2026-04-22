package cn.iocoder.yudao.module.deepay.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.deepay.agent.*;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayOrderDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignImageMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayOrderMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import cn.iocoder.yudao.module.deepay.orchestrator.ProductionOrchestrator;
import cn.iocoder.yudao.module.deepay.service.CurrencyService;
import cn.iocoder.yudao.module.deepay.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * DeepayDesignController — AI选款设计核心接口（Phase 9 STEP 18）。
 *
 * <pre>
 * POST /api/design/generate  — AI 全链路出图（≥6张）
 * POST /api/design/select    — 用户选款 + 反馈学习入库
 * GET  /api/shop/{id}        — 小店页数据（分享链接）
 * POST /api/order/create     — 下单
 * GET  /api/design/top       — 品类 Top 图
 * </pre>
 */
@Tag(name = "Deepay - AI设计选款")
@RestController
@RequestMapping("/api")
@Validated
public class DeepayDesignController {

    private static final Logger log = LoggerFactory.getLogger(DeepayDesignController.class);

    @Resource private ProductionOrchestrator  orchestrator;
    @Resource private FeedbackAgent           feedbackAgent;
    @Resource private UserProfileService      userProfileService;
    @Resource private CurrencyService         currencyService;
    @Resource private DeepayDesignImageMapper designImageMapper;
    @Resource private DeepayOrderMapper       orderMapper;
    @Resource private DeepayProductMapper     productMapper;

    // ====================================================================
    // POST /api/design/generate — AI 全链路出图
    // ====================================================================

    @PostMapping("/design/generate")
    @Operation(summary = "AI 全链路出图（≥6张）")
    public CommonResult<Map<String, Object>> generate(@Valid @RequestBody GenerateReqVO req) {
        log.info("[DeepayDesignController] generate category={} style={} market={}", req.getCategory(), req.getStyle(), req.getMarket());

        Context ctx = new Context();
        ctx.category        = req.getCategory();
        ctx.stylePreference = req.getStyle();
        ctx.targetMarket    = req.getMarket();
        ctx.priceLevel      = req.getPriceLevel();
        ctx.keyword         = req.getCategory();
        if (req.getUserId() != null) ctx.userId = req.getUserId();

        // Load user profile for personalization
        if (req.getUserId() != null) {
            userProfileService.loadProfile(String.valueOf(req.getUserId()), ctx);
        }

        orchestrator.run(ctx);

        // Collect ≥6 images: designImages + variantImages (safeImages preferred)
        Set<String> seen = new LinkedHashSet<>();
        if (ctx.safeImages != null) seen.addAll(ctx.safeImages);
        if (ctx.designImages != null) seen.addAll(ctx.designImages);
        if (ctx.variantImages != null) seen.addAll(ctx.variantImages);
        List<String> images = new ArrayList<>(seen);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("images",       images);
        resp.put("topImages",    ctx.topImages != null ? ctx.topImages.stream().map(DesignImage::getUrl).collect(Collectors.toList()) : Collections.emptyList());
        resp.put("chainCode",    ctx.chainCode);
        resp.put("finalPrompt",  ctx.finalPrompt);
        resp.put("category",     ctx.category);
        resp.put("style",        ctx.stylePreference);
        resp.put("cost",         ctx.cost);
        resp.put("suggestPrice", ctx.suggestPrice);
        resp.put("riskScore",    ctx.riskScore);
        resp.put("pendingQuestion", ctx.pendingQuestion);
        if (ctx.pendingQuestion != null) {
            resp.put("status", "PENDING_ANSWER");
        } else {
            resp.put("status", "OK");
        }
        log.info("[DeepayDesignController] generate DONE images={} chainCode={}", images.size(), ctx.chainCode);
        return success(resp);
    }

    // ====================================================================
    // POST /api/design/select — 用户选款
    // ====================================================================

    @PostMapping("/design/select")
    @Operation(summary = "用户选款 + 反馈学习入库")
    public CommonResult<Map<String, Object>> select(@Valid @RequestBody SelectReqVO req) {
        log.info("[DeepayDesignController] select userId={} selectedImage={}", req.getUserId(), req.getSelectedImage());

        Context ctx = new Context();
        ctx.selectedImage   = req.getSelectedImage();
        ctx.chainCode       = req.getChainCode();
        ctx.category        = req.getCategory();
        ctx.stylePreference = req.getStyle();
        if (req.getUserId() != null) {
            ctx.userId     = req.getUserId();
            ctx.customerId = req.getUserId();
        }

        // Rebuild scoredImages from the submitted list (for feedback learning)
        if (req.getAllImages() != null && !req.getAllImages().isEmpty()) {
            ctx.designImages = req.getAllImages();
            ctx.scoredImages = req.getAllImages().stream().map(url -> {
                DesignImage img = new DesignImage();
                img.setUrl(url);
                img.setCategory(req.getCategory());
                img.setStyle(req.getStyle());
                img.setScore(50.0);
                return img;
            }).collect(Collectors.toList());
        }

        // Run feedback agent (writes to deepay_feedback, updates deepay_design_image scores)
        feedbackAgent.run(ctx);

        // Update user profile
        if (req.getUserId() != null) {
            userProfileService.updateProfile(String.valueOf(req.getUserId()), ctx);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("selectedImage", ctx.selectedImage);
        resp.put("chainCode",     ctx.chainCode);
        resp.put("status",        "RECORDED");
        return success(resp);
    }

    // ====================================================================
    // GET /api/shop/{id} — 小店页（分享链接）
    // ====================================================================

    @GetMapping("/shop/{id}")
    @Operation(summary = "小店页数据（分享链接）")
    public CommonResult<Map<String, Object>> shopPage(
            @PathVariable("id") String id,
            @RequestParam(value = "currency", defaultValue = "CNY") String currency) {
        log.info("[DeepayDesignController] shopPage id={} currency={}", id, currency);

        DeepayProductDO product = null;
        try {
            product = productMapper.selectByChainCode(id);
        } catch (Exception e) {
            log.warn("[DeepayDesignController] shopPage 查询商品失败 id={}", id, e);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        if (product != null) {
            BigDecimal displayPrice = currencyService.convert(product.getPrice(), currency);
            resp.put("id",          id);
            resp.put("title",       product.getTitle());
            resp.put("description", product.getDescription());
            resp.put("image",       product.getMainImage());
            resp.put("price",       displayPrice);
            resp.put("currency",    currency);
            resp.put("status",      product.getStatus());
            resp.put("shareUrl",    "https://deepay.link/shop/" + id);
        } else {
            resp.put("id",       id);
            resp.put("status",   "NOT_FOUND");
            resp.put("shareUrl", "https://deepay.link/shop/" + id);
        }
        return success(resp);
    }

    // ====================================================================
    // POST /api/order/create — 下单
    // ====================================================================

    @PostMapping("/order/create")
    @Operation(summary = "下单（创建待支付订单）")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Object>> createOrder(@Valid @RequestBody OrderCreateReqVO req) {
        log.info("[DeepayDesignController] createOrder userId={} chainCode={}", req.getUserId(), req.getChainCode());

        // Idempotency check
        if (req.getUserId() != null) {
            DeepayOrderDO existing = orderMapper.selectByChainCodeAndUserId(req.getChainCode(), req.getUserId());
            if (existing != null && !"CANCELLED".equals(existing.getStatus())) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("orderId",    existing.getId());
                resp.put("paymentId",  existing.getPaymentId());
                resp.put("status",     existing.getStatus());
                resp.put("idempotent", true);
                return success(resp);
            }
        }

        DeepayOrderDO order = new DeepayOrderDO();
        order.setPaymentId(UUID.randomUUID().toString().replace("-", ""));
        order.setChainCode(req.getChainCode());
        order.setUserId(req.getUserId());
        order.setStatus("PENDING");
        order.setAmount(req.getAmount() != null ? req.getAmount() : BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId",   order.getId());
        resp.put("paymentId", order.getPaymentId());
        resp.put("status",    "PENDING");
        resp.put("payUrl",    "https://deepay.link/pay/" + order.getPaymentId());
        log.info("[DeepayDesignController] createOrder DONE orderId={} paymentId={}", order.getId(), order.getPaymentId());
        return success(resp);
    }

    // ====================================================================
    // GET /api/design/top — Top 图
    // ====================================================================

    @GetMapping("/design/top")
    @Operation(summary = "获取品类 Top 图（按评分）")
    public CommonResult<Map<String, Object>> topImages(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("[DeepayDesignController] topImages category={} limit={}", category, limit);
        List<?> images = designImageMapper.selectTopByCategory(category, Math.min(limit, 50));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("images", images);
        resp.put("count",  images.size());
        return success(resp);
    }

    // ====================================================================
    // Request VOs
    // ====================================================================

    public static class GenerateReqVO {
        private String category;
        private String style;
        private String market;
        private String priceLevel;
        private Long   userId;
        public String getCategory()   { return category; }
        public void setCategory(String v)   { this.category = v; }
        public String getStyle()      { return style; }
        public void setStyle(String v)      { this.style = v; }
        public String getMarket()     { return market; }
        public void setMarket(String v)     { this.market = v; }
        public String getPriceLevel() { return priceLevel; }
        public void setPriceLevel(String v) { this.priceLevel = v; }
        public Long getUserId()       { return userId; }
        public void setUserId(Long v) { this.userId = v; }
    }

    public static class SelectReqVO {
        @NotBlank(message = "selectedImage 不能为空")
        private String selectedImage;
        private String chainCode;
        private String category;
        private String style;
        private Long   userId;
        private List<String> allImages;
        public String getSelectedImage()  { return selectedImage; }
        public void setSelectedImage(String v)  { this.selectedImage = v; }
        public String getChainCode()      { return chainCode; }
        public void setChainCode(String v)      { this.chainCode = v; }
        public String getCategory()       { return category; }
        public void setCategory(String v)       { this.category = v; }
        public String getStyle()          { return style; }
        public void setStyle(String v)    { this.style = v; }
        public Long getUserId()           { return userId; }
        public void setUserId(Long v)     { this.userId = v; }
        public List<String> getAllImages() { return allImages; }
        public void setAllImages(List<String> v) { this.allImages = v; }
    }

    public static class OrderCreateReqVO {
        @NotBlank(message = "chainCode 不能为空")
        private String chainCode;
        private Long   userId;
        private BigDecimal amount;
        public String getChainCode()    { return chainCode; }
        public void setChainCode(String v)   { this.chainCode = v; }
        public Long getUserId()         { return userId; }
        public void setUserId(Long v)   { this.userId = v; }
        public BigDecimal getAmount()   { return amount; }
        public void setAmount(BigDecimal v)  { this.amount = v; }
    }
}
