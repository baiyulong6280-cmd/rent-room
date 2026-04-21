package cn.iocoder.yudao.module.deepay.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Deepay 库存管理表。
 *
 * <p>对应数据库表 {@code deepay_inventory}，记录每个链码商品的库存状态。</p>
 */
@TableName("deepay_inventory")
@Data
public class DeepayInventoryDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品链码，关联 deepay_style_chain.chain_code */
    private String chainCode;

    /** 当前可用库存 */
    private Integer stock;

    /**
     * 锁定库存（已下单未支付）。
     * 下单时从 stock 转移到 lockedStock；支付成功后 lockedStock 归零（货已发出）。
     */
    private Integer lockedStock;

    /**
     * 库存状态。
     * <ul>
     *   <li>{@code NORMAL} —— 库存充足（stock &gt; 2）</li>
     *   <li>{@code LOW}    —— 库存不足（0 &lt; stock &le; 2）</li>
     *   <li>{@code OUT}    —— 库存耗尽（stock == 0）</li>
     * </ul>
     */
    private String status;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

}
