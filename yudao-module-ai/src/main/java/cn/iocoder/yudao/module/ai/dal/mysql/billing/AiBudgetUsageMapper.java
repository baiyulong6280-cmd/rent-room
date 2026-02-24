package cn.iocoder.yudao.module.ai.dal.mysql.billing;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * AI 预算用量 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface AiBudgetUsageMapper extends BaseMapperX<AiBudgetUsageDO> {

    /**
     * 查询指定用户在指定周期的用量记录
     *
     * @param userId          用户编号，0 表示租户级
     * @param periodStartTime 周期开始时间
     * @return 用量记录，不存在返回 null
     */
    default AiBudgetUsageDO selectByUserAndPeriod(Long userId, LocalDateTime periodStartTime) {
        return selectOne(new LambdaQueryWrapperX<AiBudgetUsageDO>()
                .eq(AiBudgetUsageDO::getUserId, userId)
                .eq(AiBudgetUsageDO::getPeriodStartTime, periodStartTime));
    }

}
