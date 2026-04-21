package cn.iocoder.yudao.module.deepay.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 全流程唯一数据载体 —— 最终生产版，一次定死，不再变更。
 *
 * <pre>
 * keyword         → TrendAgent       → referenceImages
 * referenceImages → DesignAgent      → designImages
 * designImages    → JudgeAgent       → imageScores
 * imageScores     → AIDecisionAgent  → selectedImage / needRedesign / shouldProduce / suggestPrice / action
 * selectedImage   → ChainAgent       → chainCode
 * chainCode       → PatternAgent     → patternFile
 * keyword+image   → ProductAgent     → title / description
 * suggestPrice    → PricingAgent     → price
 * productId+price → PublishAgent     → published / productId
 * productId       → PaymentAgent     → paymentId / paid
 * price           → InventoryAgent   → stock / lockedStock
 * *               → AnalyticsAgent   → soldCount / analyticsReport
 * </pre>
 */
public class Context {

    // ===== 输入 =====
    public String keyword;

    // ===== 趋势 =====
    public List<String> referenceImages;

    // ===== 设计 =====
    public List<String> designImages;

    // ===== 评分 =====
    public Map<String, Integer> imageScores;

    // ===== AI决策 =====
    public String selectedImage;
    public Boolean needRedesign;
    public Boolean shouldProduce;
    public BigDecimal suggestPrice;
    public String decisionReason;

    // ===== 链路 =====
    public String chainCode;

    // ===== 打版 =====
    public String patternFile;
    public String techPackUrl;

    // ===== 商品 =====
    public String title;
    public String description;

    // ===== 定价 =====
    public BigDecimal price;

    // ===== 发布 =====
    public Boolean published;
    public String productId;
    public String productUrl;

    // ===== 支付 =====
    public String paymentId;
    public Boolean paid;
    public String orderId;
    public Long userId;

    // ===== 库存 =====
    public Integer stock;
    public Integer lockedStock;

    // ===== 销售 =====
    public Integer soldCount;

    // ===== 成本与利润（Phase 4 利润驱动核心） =====
    /** 生产成本（元）；ProductAgent 落库时写入，PricingAgent 据此定价 */
    public java.math.BigDecimal costPrice;
    /** 单笔利润 = price - costPrice，Payment 回调时计算 */
    public java.math.BigDecimal profit;
    /** 投资回报率 = profit / costPrice，Payment 回调 & Scheduler 决策依据 */
    public java.math.BigDecimal roi;

    // ===== 分析 =====
    public String action;           // BOOST / STOP / REDESIGN
    public String analyticsReport;

    // ===== 向后兼容（ChainOrchestrator）=====
    /** @deprecated 使用 keyword */
    @Deprecated public String prompt;
    /** @deprecated 使用 designImages */
    @Deprecated public List<String> images;
    /** @deprecated */
    @Deprecated public String imaKbId;
    /** @deprecated */
    @Deprecated public String iban;

}


