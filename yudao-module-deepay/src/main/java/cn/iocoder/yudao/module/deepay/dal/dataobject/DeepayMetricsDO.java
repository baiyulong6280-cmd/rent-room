package cn.iocoder.yudao.module.deepay.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标表 deepay_metrics
 */
@TableName("deepay_metrics")
@Data
public class DeepayMetricsDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联链码 */
    private String chainCode;

    /** 销量快照 */
    private Integer soldCount;

    /** 上架价格快照 */
    private BigDecimal price;

    /** 分类（来自 keyword） */
    private String category;

    private LocalDateTime createdAt;

}
