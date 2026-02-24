package cn.iocoder.yudao.module.ai.service.billing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.model.vo.calllog.AiModelCallLogPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiModelCallLogDO;
import jakarta.validation.Valid;

/**
 * AI 模型调用日志 Service 接口
 *
 * @author 芋道源码
 */
public interface AiModelCallLogService {

    /**
     * 创建调用日志（含自动计费）
     *
     * 根据 token 统计和模型计费配置，自动计算费用并快照单价。
     *
     * @param callLog 调用日志（不含费用字段，由本方法自动填充）
     * @return 编号
     */
    Long createCallLog(AiModelCallLogDO callLog);

    /**
     * 获得调用日志
     *
     * @param id 编号
     * @return 调用日志
     */
    AiModelCallLogDO getCallLog(Long id);

    /**
     * 获得调用日志分页
     *
     * @param pageReqVO 分页查询
     * @return 调用日志分页
     */
    PageResult<AiModelCallLogDO> getCallLogPage(@Valid AiModelCallLogPageReqVO pageReqVO);

}
