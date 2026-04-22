package cn.iocoder.yudao.module.deepay.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设计图评分结果表 deepay_design_image
 *
 * <p>由 {@link cn.iocoder.yudao.module.deepay.agent.ImageScoringAgent} 写入。</p>
 */
@TableName("deepay_design_image")
@Data
public class DeepayDesignImageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图片 CDN 地址 */
    private String url;

    /** 品类（外套 / 连衣裙 等） */
    private String category;

    /** 风格标签 */
    private String style;

    /** 综合分 */
    private Double score;

    /** 趋势分 */
    private Double trendScore;

    /** 客户匹配分 */
    private Double matchScore;

    private LocalDateTime createdAt;

}
