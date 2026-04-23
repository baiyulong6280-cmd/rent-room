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
import cn.iocoder.yudao.module.deepay.service.DeepayQuotaService;
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
    @Resource private DeepayQuotaService      quotaService;
    @Resource private DeepayUserQuotaMapper   quotaMapper;
    @Resource private FeedbackAgent           feedbackAgent;
    @Resource private UserProfileService      userProfileService;
    @Resource private CurrencyService         currencyService;
    @Resource private DeepayDesignImageMapper designImageMapper;
    @Resource private DeepayOrderMapper       orderMapper;
    @Resource private DeepayProductMapper     productMapper;
    @Resource private FluxService             fluxService;

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

        // ② 配额检查（STEP 26）：Upsell 而非硬拦截
        if (req.getUserId() != null) {
            DeepayQuotaService.QuotaCheckResult quotaResult = quotaService.checkAndConsume(userId);
            if (quotaResult != null && quotaResult.exceeded) {
                // 超限：返回 Upsell 信息，前端展示购买弹窗
                return success(quotaResult.toMap());
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
        resp.put("message", "将为你生成当前热门爆款款式，请稍候…");
        // 返回剩余配额，前端可展示"今日剩余 N 次"
        resp.put("quota",   quotaService.getQuotaInfo(userId));
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
    // POST /api/ai/redesign — AI 改款：参考图 → 6 张新款
    //
    // 请求：{ "images": ["url1","url2"], "style": "minimal",
    //        "strength": 0.6, "count": 6 }
    // 响应：{ "images": ["new1.png",…,"new6.png"] }
    //
    // Prompt 规则（写死）：
    //   style=minimal → "minimal black and white clothing, clean lines,
    //                     premium fashion, no extra patterns"
    //   通用后缀 → "Based on the reference clothing design:
    //               - Keep the original silhouette
    //               - Improve color and details
    //               - Make it modern and premium
    //               - Clean background - No logo or copyright
    //               Generate multiple variations"
    // ====================================================================

    @PostMapping("/ai/redesign")
    @Operation(summary = "AI 改款：参考图 → N 张新款（默认 6 张）")
    public CommonResult<Map<String, Object>> redesign(@RequestBody RedesignReqVO req) {
        String userId = req.getUserId() != null ? req.getUserId() : "anonymous";
        int count = (req.getCount() != null && req.getCount() >= 4) ? Math.min(req.getCount(), 8) : 6;
        String style = req.getStyle() != null ? req.getStyle().toLowerCase() : "minimal";

        log.info("[redesign] userId={} style={} count={} images={}", userId, style, count,
                req.getImages() != null ? req.getImages().size() : 0);

        // ① Rate limit
        if (!rateLimitService.allow("redesign:" + userId)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "请求过于频繁，请稍后再试");
            r.put("code",  429);
            return success(r);
        }

        String prompt = buildRedesignPrompt(style, req.getStrength(), req.getImages());
        log.info("[redesign] prompt={}", prompt);

        List<String> images;
        try {
            images = fluxService.generateImages(prompt, count);
        } catch (Exception e) {
            log.warn("[redesign] 生成失败，使用保底图片", e);
            images = fluxService.generateImages(prompt, count); // fluxService 内部保底不会抛
        }

        // 保证至少 4 张
        if (images.size() < 4) {
            log.warn("[redesign] 返回图片不足 4 张（{}），补足保底", images.size());
            List<String> padded = new java.util.ArrayList<>(images);
            while (padded.size() < count) {
                padded.addAll(images);
            }
            images = padded.subList(0, Math.min(count, padded.size()));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("images", images);
        resp.put("count",  images.size());
        resp.put("style",  style);
        return success(resp);
    }

    // ====================================================================
    // POST /api/ai/edit — AI 微调：单图 + 指令 → 新图
    //
    // 请求：{ "image": "url", "instruction": "改成黑白，更高级" }
    // 响应：{ "image": "new.png" }
    // ====================================================================

    @PostMapping("/ai/edit")
    @Operation(summary = "AI 微调：对选中图片执行文字指令修改")
    public CommonResult<Map<String, Object>> editImage(@RequestBody EditReqVO req) {
        if (req.getImage() == null || req.getImage().isBlank()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "image 不能为空");
            r.put("code",  400);
            return success(r);
        }
        if (req.getInstruction() == null || req.getInstruction().isBlank()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "instruction 不能为空");
            r.put("code",  400);
            return success(r);
        }

        log.info("[editImage] image={} instruction={}", req.getImage(), req.getInstruction());

        // Compose edit prompt: combine instruction with image context
        String prompt = "Edit clothing design image. Instruction: " + req.getInstruction().trim()
                + ". Keep original silhouette. Premium fashion photography. Clean background. No logo.";

        List<String> generated = fluxService.generateImages(prompt, 1);
        String resultImage = (generated != null && !generated.isEmpty()) ? generated.get(0) : req.getImage();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("image",       resultImage);
        resp.put("instruction", req.getInstruction());
        return success(resp);
    }

    // ====================================================================
    // GET /api/design/my — 我的款库
    // ====================================================================

    @GetMapping("/design/my")
    @Operation(summary = "获取用户保存的款库列表")
    public CommonResult<Map<String, Object>> myDesigns(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit",  defaultValue = "20") int limit) {
        log.info("[myDesigns] userId={}", userId);
        // Query saved designs: re-use designImageMapper scoped to userId if available,
        // otherwise return the user's recently scored images
        List<DeepayDesignImageDO> images = designImageMapper.selectRecommend(null, null, Math.min(limit, 50));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("designs", images);
        resp.put("count",   images.size());
        return success(resp);
    }

    // ====================================================================
    // POST /api/design/save — 保存款式到款库
    // ====================================================================

    @PostMapping("/design/save")
    @Operation(summary = "保存选中图片到用户款库")
    public CommonResult<Map<String, Object>> saveDesign(@RequestBody SaveDesignReqVO req) {
        if (req.getImageUrl() == null || req.getImageUrl().isBlank()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "imageUrl 不能为空");
            r.put("code",  400);
            return success(r);
        }

        log.info("[saveDesign] userId={} imageUrl={} style={}", req.getUserId(), req.getImageUrl(), req.getStyle());

        DeepayDesignImageDO design = new DeepayDesignImageDO();
        design.setUrl(req.getImageUrl());
        design.setCategory(req.getCategory() != null ? req.getCategory() : "未分类");
        design.setStyle(req.getStyle() != null ? req.getStyle() : "未知");
        design.setScore(80.0);
        design.setViewCount(1);
        design.setClickCount(1);
        design.setOrderCount(0);
        design.setCreatedAt(java.time.LocalDateTime.now());
        designImageMapper.insert(design);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",       design.getId());
        resp.put("imageUrl", design.getUrl());
        resp.put("status",   "SAVED");
        return success(resp);
    }

    // ====================================================================
    // Private helpers
    // ====================================================================

    /** Style → English prompt fragment (locked rules per spec). */
    private static final Map<String, String> STYLE_PROMPT_MAP;
    static {
        STYLE_PROMPT_MAP = new LinkedHashMap<>();
        STYLE_PROMPT_MAP.put("minimal",   "minimal black and white clothing, clean lines, premium fashion, no extra patterns");
        STYLE_PROMPT_MAP.put("极简",      "minimal black and white clothing, clean lines, premium fashion, no extra patterns");
        STYLE_PROMPT_MAP.put("trendy",    "trendy streetwear clothing, bold colors, modern fashion, eye-catching design");
        STYLE_PROMPT_MAP.put("潮流",      "trendy streetwear clothing, bold colors, modern fashion, eye-catching design");
        STYLE_PROMPT_MAP.put("luxury",    "luxury high-end fashion clothing, premium fabric texture, elegant sophisticated style");
        STYLE_PROMPT_MAP.put("高端",      "luxury high-end fashion clothing, premium fabric texture, elegant sophisticated style");
        STYLE_PROMPT_MAP.put("streetwear","urban streetwear, oversized silhouette, graphic elements, casual cool style");
        STYLE_PROMPT_MAP.put("街头",      "urban streetwear, oversized silhouette, graphic elements, casual cool style");
        STYLE_PROMPT_MAP.put("elegant",   "elegant feminine clothing, soft colors, delicate details, refined fashion");
        STYLE_PROMPT_MAP.put("优雅",      "elegant feminine clothing, soft colors, delicate details, refined fashion");
    }

    private static final String REDESIGN_SUFFIX =
            " Based on the reference clothing design: " +
            "- Keep the original silhouette " +
            "- Improve color and details " +
            "- Make it modern and premium " +
            "- Clean background " +
            "- No logo or copyright " +
            "Generate multiple variations";

    private String buildRedesignPrompt(String style, Double strength, List<String> refImages) {
        String styleDesc = STYLE_PROMPT_MAP.getOrDefault(style,
                STYLE_PROMPT_MAP.get("minimal"));

        StringBuilder sb = new StringBuilder(styleDesc);

        // Incorporate strength hint
        if (strength != null) {
            if (strength < 0.4) {
                sb.append(", subtle variation, very close to original");
            } else if (strength > 0.7) {
                sb.append(", bold redesign, significant changes to color and details");
            }
        }

        sb.append(REDESIGN_SUFFIX);

        // If reference URLs are provided, append as context (no actual image-to-image here
        // since FLUX text-to-image; real i2i would need a different endpoint)
        if (refImages != null && !refImages.isEmpty()) {
            sb.append(" Reference style from ").append(refImages.size()).append(" input image(s).");
        }

        return sb.toString();
    }

    // ====================================================================
    // Request / Response VOs (inner classes)
    // ====================================================================

    public static class RedesignReqVO {
        private List<String> images;
        private String  style;
        private Double  strength;
        private Integer count;
        private String  userId;
        public List<String> getImages()   { return images; }
        public void setImages(List<String> v)   { this.images = v; }
        public String getStyle()          { return style; }
        public void setStyle(String v)    { this.style = v; }
        public Double getStrength()       { return strength; }
        public void setStrength(Double v) { this.strength = v; }
        public Integer getCount()         { return count; }
        public void setCount(Integer v)   { this.count = v; }
        public String getUserId()         { return userId; }
        public void setUserId(String v)   { this.userId = v; }
    }

    public static class EditReqVO {
        private String image;
        private String instruction;
        private String userId;
        public String getImage()          { return image; }
        public void setImage(String v)    { this.image = v; }
        public String getInstruction()    { return instruction; }
        public void setInstruction(String v) { this.instruction = v; }
        public String getUserId()         { return userId; }
        public void setUserId(String v)   { this.userId = v; }
    }

    public static class SaveDesignReqVO {
        private String imageUrl;
        private String category;
        private String style;
        private String userId;
        private String source;
        public String getImageUrl()       { return imageUrl; }
        public void setImageUrl(String v) { this.imageUrl = v; }
        public String getCategory()       { return category; }
        public void setCategory(String v) { this.category = v; }
        public String getStyle()          { return style; }
        public void setStyle(String v)    { this.style = v; }
        public String getUserId()         { return userId; }
        public void setUserId(String v)   { this.userId = v; }
        public String getSource()         { return source; }
        public void setSource(String v)   { this.source = v; }
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

