package cn.iocoder.yudao.module.deepay.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表 deepay_product
 */
@TableName("deepay_product")
@Data
public class DeepayProductDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联链码 */
    private String chainCode;

    /** 商品标题 */
    private String title;

    /** 售价 */
    private BigDecimal price;

    /** 状态：SELLING / STOPPED / REDESIGNING */
    private String status;

    /** 销量 */
    private Integer soldCount;

    /** 可用库存 */
    private Integer stock;

    private LocalDateTime createdAt;

}
