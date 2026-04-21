package cn.iocoder.yudao.module.deepay.agent;

import java.util.List;
import java.util.Map;

/**
 * 全流程唯一数据载体。
 *
 * <p>所有 Agent 只读 / 只写本对象的字段，相互之间不直接调用，从而保持简单与可替换性。
 * 后续接入真实 AI / 支付时只需替换对应 Agent，无需改动其他部分。</p>
 *
 * <p>完整流水线字段说明：</p>
 * <pre>
 * prompt          → TrendAgent   → trendKeyword
 * trendKeyword    → DesignAgent  → images
 * images          → JudgeAgent   → imageScores
 * imageScores     → AIDecisionAgent → selectedImage
 * selectedImage   → ChainAgent   → chainCode
 * chainCode       → PatternAgent → patternCode
 * patternCode     → ProductAgent → productRecordId
 * productRecordId → PricingAgent → price
 * price           → PublishAgent → publishStatus
 * publishStatus   → PaymentAgent → jeepayLink / swanLink
 * jeepayLink      → InventoryAgent → initialStock
 * initialStock    → AnalyticsAgent → analyticsReport
 * </pre>
 */
public class Context {

    // ==================== 阶段 0：用户输入 ====================

    /** 用户输入的一句话需求，例如"极简羊绒大衣" */
    public String prompt;

    // ==================== 阶段 1：TrendAgent（找爆款）====================

    /** 爆款趋势关键词（由 TrendAgent 从 prompt 中提取或匹配） */
    public String trendKeyword;

    // ==================== 阶段 2：DesignAgent（改款）====================

    /** DesignAgent 输出的候选图片 URL 列表（MVP 固定 3 张） */
    public List<String> images;

    // ==================== 阶段 3：JudgeAgent（视觉评分）====================

    /** 各图视觉评分：图片列表下标 → 评分 (0-100) */
    public Map<Integer, Integer> imageScores;

    // ==================== 阶段 4：AIDecisionAgent（核心决策）====================

    /** AIDecisionAgent 从 images 中选中的最高分图片 URL */
    public String selectedImage;

    // ==================== 阶段 5：ChainAgent（打版前置：生成链码）====================

    /** ChainAgent 生成并落库的 6 位链码，商品唯一标识 */
    public String chainCode;

    // ==================== 阶段 6：PatternAgent（打版）====================

    /** 打版编码，格式：PAT-{chainCode}-{样式后缀} */
    public String patternCode;

    // ==================== 阶段 7：ProductAgent（商品生成）====================

    /** ProductAgent 写库后的样式链记录 ID */
    public Long productRecordId;

    // ==================== 阶段 8：PricingAgent（AI定价）====================

    /** 最终定价，单位：分（例如 29900 = 299.00 元） */
    public Integer price;

    // ==================== 阶段 9：PublishAgent（上架）====================

    /** 上架状态：PUBLISHED */
    public String publishStatus;

    // ==================== 阶段 10：PaymentAgent（收款）====================

    /** Jeepay 收款链接 */
    public String jeepayLink;

    /** Swan 收款链接 */
    public String swanLink;

    /** FinanceAgent（旧字段保留兼容）生成的收款 IBAN */
    public String iban;

    // ==================== 阶段 10.5：ImaAgent（ima 知识库同步，可选）====================

    /** ImaAgent 创建的 ima 知识库 ID（同步失败时为 null） */
    public String imaKbId;

    // ==================== 阶段 11：InventoryAgent（库存智能）====================

    /** 初始库存数量（由 InventoryAgent 设定） */
    public Integer initialStock;

    // ==================== 阶段 12：AnalyticsAgent（复盘优化）====================

    /** AnalyticsAgent 生成的复盘摘要报告 */
    public String analyticsReport;

}

