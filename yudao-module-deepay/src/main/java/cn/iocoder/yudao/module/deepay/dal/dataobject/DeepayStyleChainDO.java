package cn.iocoder.yudao.module.deepay.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Deepay 一衣一链 —— 样式链码表。
 *
 * <p>对应数据库表 {@code deepay_style_chain}。
 * 不继承 BaseDO，保持表结构轻量，仅包含业务必要字段。</p>
 */
@TableName("deepay_style_chain")
@Data
public class DeepayStyleChainDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 6 位随机大写字母 + 数字链码，全局唯一 */
    private String chainCode;

    /** 最终选中的设计图片 URL */
    private String imageUrl;

    /**
     * 记录状态。
     * <ul>
     *   <li>{@code CREATED}   —— 已创建链码</li>
     *   <li>{@code PUBLISHED} —— 已上架</li>
     * </ul>
     */
    private String status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** ima 知识库 ID（异步同步成功后写入，失败时为 null） */
    private String imaKbId;

    // ==================== 第三阶段：完整流水线扩展字段 ====================

    /** TrendAgent 识别的爆款关键词 */
    private String trendKeyword;

    /** PatternAgent 生成的打版编码 */
    private String patternCode;

    /** PricingAgent 定价（单位：分） */
    private Integer price;

    /** PaymentAgent 合并收款链接（Jeepay|Swan 格式） */
    private String paymentLink;

    /** InventoryAgent 初始库存数量 */
    private Integer initialStock;

}

