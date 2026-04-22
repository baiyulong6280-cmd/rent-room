package cn.iocoder.yudao.module.deepay.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.deepay.agent.*;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignImageDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayOrderDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayTaskDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayDesignImageMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayOrderMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayProductMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayTaskMapper;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayUserQuotaMapper;
import cn.iocoder.yudao.module.deepay.service.CurrencyService;
import cn.iocoder.yudao.module.deepay.service.DeepayRateLimitService;
import cn.iocoder.yudao.module.deepay.service.DeepayTaskAsyncService;
import cn.iocoder.yudao.module.deepay.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * DeepayDesignController — AI选款设计核心接口（Phase 9 STEP 21+）。
 *
 * <pre>
 * POST /api/design/generate     — 创建异步出图任务，立即返回 taskId（STEP 21）
 * GET  /api/design/result/{id}  — 轮询任务结果（STEP 21）
 * POST /api/design/select       — 用户选款 + 反馈学习入库
 * GET  /api/design/recommend    — 个性化推荐（STEP 29）
 * GET  /api/shop/{id}           — 小店页数据（分享链接）
 * POST /api/order/create        — 下单
 * GET  /api/design/top          — 品类 Top 图
 * </pre>
 */
@Tag(name = "Deepay - AI设计选款")
@RestController
@RequestMapping("/api")
@Validated
public class DeepayDesignController {

    private static final Logger log = LoggerFactory.getLogger(DeepayDesignController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource private DeepayTaskMapper        taskMapper;
    @Resource private DeepayTaskAsyncService  asyncService;
    @Resource private DeepayRateLimitService  rateLimitService;
    @Resource private DeepayUserQuotaMapper   quotaMapper;
    @Resource private FeedbackAgent           feedbackAgent;
    @Resource private UserProfileService      userProfileService;
    @Resource private CurrencyService         currencyService;
    @Resource private DeepayDesignImageMapper designImageMapper;
    @Resource private DeepayOrderMapper       orderMapper;
    @Resource private DeepayProductMapper     productMapper;

    // ====================================================================
    // POST /api/design/generate — 创建异步出图任务（STEP 21）
    // ====================================================================

    @PostMapping("/design/generate")
    @Operation(summary = "创建 AI 出图任务（异步），立即返回 taskId")
    public CommonResult<Map<String, Object>> generate(@Valid @RequestBody GenerateReqVO req) {
        String userId = req.getUserId() != null ? String.valueOf(req.getUserId()) : "anonymous";
        log.info("[generate] category={} style={} market={} userId={}",
                req.getCategory(), req.getStyle(), req.getMarket(), userId);

        // ① 限流（STEP 23）：1 分钟内最多 3 次
        if (!rateLimitService.allow(userId)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "请求过于频繁，请 1 分钟后再试");
            r.put("code",  429);
            return success(r);
        }

        // ② 配额检查（STEP 26）：初始化并扣减
        if (req.getUserId() != null) {
            quotaMapper.initIfAbsent(userId);
            int consumed = quotaMapper.consumeQuota(userId);
            if (consumed == 0) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("error", "出图次数已用完，请充值后继续");
                r.put("code",  402);
                return success(r);
            }
        }

        // ③ 创建任务记录（STEP 21）
        DeepayTaskDO task = new DeepayTaskDO();
        task.setUserId(userId);
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // ④ 构建 Context
        Context ctx = new Context();
        ctx.category        = req.getCategory();
        ctx.stylePreference = req.getStyle();
        ctx.targetMarket    = req.getMarket();
        ctx.market          = req.getMarket();
        ctx.priceLevel      = req.getPriceLevel();
        ctx.keyword         = req.getCategory();
        if (req.getUserId() != null) ctx.userId = req.getUserId();

        // 加载用户历史画像
        if (req.getUserId() != null) {
            userProfileService.loadProfile(userId, ctx);
        }

        // ⑤ 异步执行（STEP 21）
        asyncService.runTask(task.getId(), ctx);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("taskId",  task.getId());
        resp.put("status",  "pending");
        resp.put("message", "任务已创建，请通过 /api/design/result/{taskId} 轮询结果");
        log.info("[generate] 任务已创建 taskId={} userId={}", task.getId(), userId);
        return success(resp);
    }

    // ====================================================================
    // GET /api/design/result/{id} — 轮询任务结果（STEP 21）
    // ====================================================================

    @GetMapping("/design/result/{id}")
    @Operation(summary = "轮询异步出图任务结果")
    public CommonResult<Map<String, Object>> result(@PathVariable("id") Long id) {
        DeepayTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "任务不存在");
            r.put("code",  404);
            return success(r);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("taskId", task.getId());
        resp.put("status", task.getStatus());

        if ("success".equals(task.getStatus())) {
            // 反序列化图片列表
            List<String> images = Collections.emptyList();
            try {
                if (task.getResult() != null) {
                    images = MAPPER.readValue(task.getResult(), new TypeReference<List<String>>() {});
                }
            } catch (Exception e) {
                log.warn("[result] 反序列化 result 失败 taskId={}", id, e);
            }
            resp.put("images", images);
            resp.put("count",  images.size());
        } else if ("failed".equals(task.getStatus())) {
            resp.put("error", task.getErrorMsg());
        }

        return success(resp);
    }

    // ====================================================================
    // POST /api/design/select — 用户选款
    // ====================================================================

    @PostMapping("/design/select")
    @Operation(summary = "用户选款 + 反馈学习入库")
    public CommonResult<Map<String, Object>> select(@Valid @RequestBody SelectReqVO req) {
        log.info("[select] userId={} selectedImage={}", req.getUserId(), req.getSelectedImage());

        Context ctx = new Context();
        ctx.selectedImage   = req.getSelectedImage();
        ctx.chainCode       = req.getChainCode();
        ctx.category        = req.getCategory();
        ctx.stylePreference = req.getStyle();
        if (req.getUserId() != null) {
            ctx.userId     = req.getUserId();
            ctx.customerId = req.getUserId();
        }

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

        feedbackAgent.run(ctx);

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
    // GET /api/design/recommend — 个性化推荐（STEP 29）
    // ====================================================================

    @GetMapping("/design/recommend")
    @Operation(summary = "个性化图片推荐（按用户品类+风格偏好，评分降序）")
    public CommonResult<Map<String, Object>> recommend(
            @RequestParam(value = "category",  required = false) String category,
            @RequestParam(value = "style",     required = false) String style,
            @RequestParam(value = "userId",    required = false) Long userId,
            @RequestParam(value = "limit",     defaultValue = "10") int limit) {

        // 如果未传 category/style，尝试从用户画像加载
        if (userId != null && (category == null || style == null)) {
            Context tmp = new Context();
            userProfileService.loadProfile(String.valueOf(userId), tmp);
            if (category == null) category = tmp.category;
            if (style    == null) style    = tmp.stylePreference;
        }

        log.info("[recommend] category={} style={} userId={} limit={}", category, style, userId, limit);
        List<DeepayDesignImageDO> images = designImageMapper.selectRecommend(category, style, Math.min(limit, 50));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("images",   images);
        resp.put("count",    images.size());
        resp.put("category", category);
        resp.put("style",    style);
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
        log.info("[shopPage] id={} currency={}", id, currency);

        DeepayProductDO product = null;
        try {
            product = productMapper.selectByChainCode(id);
        } catch (Exception e) {
            log.warn("[shopPage] 查询商品失败 id={}", id, e);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        if (product != null) {
            BigDecimal displayPrice = currencyService.convert(product.getPrice(), currency);
            resp.put("id",          id);
            resp.put("title",       product.getTitle());
            resp.put("description", product.getDescription());
            resp.put("image",       product.getMainImage());
            resp.put("designId",    product.getDesignId());
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
        log.info("[createOrder] userId={} chainCode={}", req.getUserId(), req.getChainCode());

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
        log.info("[createOrder] DONE orderId={} paymentId={}", order.getId(), order.getPaymentId());
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
        log.info("[topImages] category={} limit={}", category, limit);
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

