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

    // ===== Phase 5 B2B 批发 =====
    /** 客户 ID（批发场景） */
    public Long clientId;
    /** 客户等级 A/B/C，由 ClientAgent 注入，影响定价与供货优先级 */
    public String clientLevel;
    /** 批发数量（下单件数，影响阶梯定价） */
    public Integer wholesaleQty;
    /** 批发折扣后的单价 */
    public java.math.BigDecimal wholesalePrice;
    /** 本单批发总利润 = (wholesalePrice - costPrice) × wholesaleQty */
    public java.math.BigDecimal wholesaleProfit;
    /** DemandAgent 预测销量（未来 7 天） */
    public Integer predictedDemand;
    /** DemandAgent 预测置信度（0~1） */
    public java.math.BigDecimal demandConfidence;
    /** ProductionPlanner 建议生产量 */
    public Integer suggestedProductionQty;

    // ===== 分析 =====
    public String action;           // BOOST / STOP / REDESIGN
    public String analyticsReport;

    // ===== Phase 6 客户画像（记忆 + 个性化）=====
    /** 客户 ID（B2B 客户或用户 ID，关联 deepay_customer_profile） */
    public Long customerId;
    /** 品类（如 外套 / 内裤 / 连衣裙），SmartQuestionAgent 填充或从画像加载 */
    public String category;
    /** 风格（如 minimalist / streetwear / luxury） */
    public String style;
    /** 目标市场：CN / EU / US / ME */
    public String market;
    /** 价格带：LOW / MID / HIGH */
    public String priceLevel;
    /** 目标年龄：YOUNG / MIDDLE / ELDER */
    public String targetAge;
    /** 目标性别：MALE / FEMALE / UNISEX */
    public String gender;
    /** 客户画像置信度（0~1），低于 0.6 时触发 SmartQuestionAgent */
    public java.math.BigDecimal confidenceScore;
    /** TrendSourceAgent 输出：趋势图 URL 列表（来自内部近7天热销） */
    public java.util.List<String> trendImages;
    /** TrendSourceAgent 输出：趋势关键词 */
    public java.util.List<String> trendKeywords;

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


