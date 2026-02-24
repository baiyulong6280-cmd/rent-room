package cn.iocoder.yudao.module.ai.controller.admin.model;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.ai.controller.admin.model.vo.budget.AiBudgetUsageRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetConfigDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
import cn.iocoder.yudao.module.ai.enums.billing.AiBudgetPeriodTypeEnum;
import cn.iocoder.yudao.module.ai.service.billing.AiBudgetConfigService;
import cn.iocoder.yudao.module.ai.service.billing.AiBudgetUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - AI 预算使用情况")
@RestController
@RequestMapping("/ai/budget-usage")
@Validated
public class AiBudgetUsageController {

    @Resource
    private AiBudgetUsageService budgetUsageService;

    @Resource
    private AiBudgetConfigService budgetConfigService;

    @GetMapping("/get")
    @Operation(summary = "获得当前周期预算使用情况")
    @Parameter(name = "userId", description = "用户编号，0 表示租户级", required = true, example = "0")
    @PreAuthorize("@ss.hasPermission('ai:budget-usage:query')")
    public CommonResult<AiBudgetUsageRespVO> getBudgetUsage(@RequestParam("userId") Long userId) {
        // 当前月份的 1 号 00:00
        LocalDateTime periodStart = LocalDateTime.now()
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 查询用量
        AiBudgetUsageDO usage = budgetUsageService.getUsage(userId, periodStart);
        long usedAmount = usage != null ? usage.getUsedAmount() : 0L;

        // 查询预算配置
        AiBudgetConfigDO config = budgetConfigService.getBudgetConfig(userId, AiBudgetPeriodTypeEnum.MONTHLY.getType());
        Long budgetAmount = config != null ? config.getBudgetAmount() : null;

        // 构建响应
        AiBudgetUsageRespVO respVO = new AiBudgetUsageRespVO();
        respVO.setUserId(userId);
        respVO.setPeriodStartTime(periodStart);
        respVO.setCurrency("CNY");
        respVO.setUsedAmount(usedAmount);
        respVO.setUsedAmountYuan(usedAmount / 1_000_000.0);
        if (budgetAmount != null) {
            respVO.setBudgetAmount(budgetAmount);
            respVO.setBudgetAmountYuan(budgetAmount / 1_000_000.0);
            long remain = Math.max(budgetAmount - usedAmount, 0);
            respVO.setRemainAmount(remain);
            respVO.setRemainAmountYuan(remain / 1_000_000.0);
            respVO.setUsagePercent(budgetAmount > 0 ? Math.round(usedAmount * 10000.0 / budgetAmount) / 100.0 : 0.0);
        }
        return success(respVO);
    }

}
