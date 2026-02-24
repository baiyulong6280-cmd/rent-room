package cn.iocoder.yudao.module.ai.dal.mysql.billing;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 预算配置 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface AiBudgetConfigMapper extends BaseMapperX<AiBudgetConfigDO> {

    /**
     * 查询指定租户和用户的预算配置
     *
     * @param userId     用户编号，0 表示租户级
     * @param periodType 周期类型
     * @return 预算配置，不存在返回 null
     */
    default AiBudgetConfigDO selectByUserAndPeriod(Long userId, String periodType) {
        return selectOne(new LambdaQueryWrapperX<AiBudgetConfigDO>()
                .eq(AiBudgetConfigDO::getUserId, userId)
                .eq(AiBudgetConfigDO::getPeriodType, periodType));
    }

}
