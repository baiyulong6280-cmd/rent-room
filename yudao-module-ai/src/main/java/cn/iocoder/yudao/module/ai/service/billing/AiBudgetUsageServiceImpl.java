package cn.iocoder.yudao.module.ai.service.billing;

import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
import cn.iocoder.yudao.module.ai.dal.mysql.billing.AiBudgetUsageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

/**
 * AI 预算用量 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class AiBudgetUsageServiceImpl implements AiBudgetUsageService {

    @Resource
    private AiBudgetUsageMapper budgetUsageMapper;

    @Override
    public AiBudgetUsageDO getUsage(Long userId, LocalDateTime periodStartTime) {
        return budgetUsageMapper.selectByUserAndPeriod(userId, periodStartTime);
    }

    @Override
    public void addUsage(Long userId, LocalDateTime periodStartTime, long deltaAmount) {
        AiBudgetUsageDO usage = budgetUsageMapper.selectByUserAndPeriod(userId, periodStartTime);
        if (usage == null) {
            // 首次：插入
            usage = AiBudgetUsageDO.builder()
                    .userId(userId)
                    .periodStartTime(periodStartTime)
                    .currency("CNY")
                    .usedAmount(deltaAmount)
                    .version(0)
                    .build();
            budgetUsageMapper.insert(usage);
        } else {
            // 累加
            AiBudgetUsageDO updateObj = new AiBudgetUsageDO();
            updateObj.setId(usage.getId());
            updateObj.setUsedAmount(usage.getUsedAmount() + deltaAmount);
            budgetUsageMapper.updateById(updateObj);
        }
    }

}
