package cn.iocoder.yudao.module.ai.dal.dataobject.billing;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 模型计费配置 DO
 *
 * @author 芋道源码
 */
@TableName("ai_model_pricing")
@KeySequence("ai_model_pricing_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelPricingDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 模型编号，关联 ai_model.id
     */
    private Long modelId;
    /**
     * 币种，首期固定 CNY
     */
    private String currency;
    /**
     * 输入单价：微元/100万 tokens（缓存未命中）
     */
    private Long priceInPer1m;
    /**
     * 缓存命中输入单价：微元/100万 tokens，0 表示与输入同价（不区分）
     */
    private Long priceCachedPer1m;
    /**
     * 输出单价：微元/100万 tokens（标准输出）
     */
    private Long priceOutPer1m;
    /**
     * 推理/思考输出单价：微元/100万 tokens，0 表示与输出同价（不区分）
     */
    private Long priceReasoningPer1m;
    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}
