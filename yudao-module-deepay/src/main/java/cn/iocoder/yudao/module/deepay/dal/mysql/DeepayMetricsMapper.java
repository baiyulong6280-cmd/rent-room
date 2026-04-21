package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayMetricsDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface DeepayMetricsMapper extends BaseMapperX<DeepayMetricsDO> {

    default void incrementSoldCount(Long id) {
        update(null, new LambdaUpdateWrapper<DeepayMetricsDO>()
                .setSql("sold_count = sold_count + 1")
                .eq(DeepayMetricsDO::getId, id));
    }

    /**
     * 查询同品类历史平均销量（用于 JudgeAgent 动态打分）。
     * 无记录时返回 null。
     */
    @Select("SELECT AVG(sold_count) FROM deepay_metrics WHERE category = #{keyword} AND sold_count IS NOT NULL")
    Double selectAvgSoldCountByCategory(String keyword);

    /**
     * 查询同品类历史平均价格（用于 AIDecisionAgent 动态定价）。
     * 无记录时返回 null。
     */
    @Select("SELECT AVG(price) FROM deepay_metrics WHERE category = #{keyword} AND price IS NOT NULL")
    BigDecimal selectAvgPriceByCategory(String keyword);

    /**
     * 查询全局历史平均销量（用于 DeepayReviewScheduler 动态阈值）。
     * 无记录时返回 null。
     */
    @Select("SELECT AVG(sold_count) FROM deepay_metrics WHERE sold_count IS NOT NULL")
    Double selectAvgSoldCount();

    /**
     * 查询全局历史销量标准差（用于 DeepayReviewScheduler 动态阈值）。
     * 无记录时返回 null。
     */
    @Select("SELECT STDDEV(sold_count) FROM deepay_metrics WHERE sold_count IS NOT NULL")
    Double selectStddevSoldCount();

}
