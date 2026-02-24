package cn.iocoder.yudao.module.ai.dal.mysql.billing;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiModelCallLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * AI 模型调用日志 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface AiModelCallLogMapper extends BaseMapperX<AiModelCallLogDO> {

    default PageResult<AiModelCallLogDO> selectPage(PageParam pageParam,
                                                    Long userId, String bizType,
                                                    String platform, Long modelId,
                                                    String status, Boolean blocked,
                                                    LocalDateTime[] requestTime) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AiModelCallLogDO>()
                .eqIfPresent(AiModelCallLogDO::getUserId, userId)
                .eqIfPresent(AiModelCallLogDO::getBizType, bizType)
                .eqIfPresent(AiModelCallLogDO::getPlatform, platform)
                .eqIfPresent(AiModelCallLogDO::getModelId, modelId)
                .eqIfPresent(AiModelCallLogDO::getStatus, status)
                .eqIfPresent(AiModelCallLogDO::getBlocked, blocked)
                .betweenIfPresent(AiModelCallLogDO::getRequestTime, requestTime)
                .orderByDesc(AiModelCallLogDO::getId));
    }

}
