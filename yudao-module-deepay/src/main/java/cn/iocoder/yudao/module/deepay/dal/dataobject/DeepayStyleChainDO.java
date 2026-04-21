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
     *   <li>{@code CREATED} —— 已创建，等待后续流程</li>
     * </ul>
     */
    private String status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

}
