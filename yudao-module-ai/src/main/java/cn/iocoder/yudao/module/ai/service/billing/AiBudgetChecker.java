package cn.iocoder.yudao.module.ai.service.billing;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetConfigDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetLogDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
import cn.iocoder.yudao.module.ai.enums.billing.AiBudgetEventTypeEnum;
import cn.iocoder.yudao.module.ai.enums.billing.AiBudgetPeriodTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.ai.enums.ErrorCodeConstants.BUDGET_EXCEEDED;

/**
 * AI 预算校验器
 *
 * 使用 Redis Lua 脚本实现双维度（用户 + 租户）原子预扣费：
 * - 调用前：预扣估算费用，任一维度超额则拦截
 * - 调用后：用实际费用修正差额
 * - 失败/取消：释放预扣占用
 *
 * Redis Key 格式：ai:budget:{tenantId}:{userId}:{periodStart}
 * - userId=0 表示租户级
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class AiBudgetChecker {

    private static final String KEY_PREFIX = "ai:budget:";
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Lua 脚本：双维度原子预扣费
     *
     * KEYS[1] = 用户 budget key
     * KEYS[2] = 租户 budget key（可能与 KEYS[1] 相同，当 userId=0 时）
     * ARGV[1] = 用户预算上限（-1 表示无限制）
     * ARGV[2] = 租户预算上限（-1 表示无限制）
     * ARGV[3] = 预扣金额
     *
     * 返回值：
     * 0 = 成功
     * 1 = 用户预算不足
     * 2 = 租户预算不足
     */
    private static final String LUA_PRE_DEDUCT = """
            local userKey = KEYS[1]
            local tenantKey = KEYS[2]
            local userLimit = tonumber(ARGV[1])
            local tenantLimit = tonumber(ARGV[2])
            local amount = tonumber(ARGV[3])

            local userUsed = tonumber(redis.call('GET', userKey) or '0')
            local tenantUsed = tonumber(redis.call('GET', tenantKey) or '0')

            -- 检查用户预算（-1 表示无限制）
            if userLimit >= 0 and (userUsed + amount) > userLimit then
                return 1
            end
            -- 检查租户预算（-1 表示无限制）
            if tenantLimit >= 0 and (tenantUsed + amount) > tenantLimit then
                return 2
            end

            -- 原子扣减
            redis.call('INCRBY', userKey, amount)
            if userKey ~= tenantKey then
                redis.call('INCRBY', tenantKey, amount)
            end
            return 0
            """;

    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>(LUA_PRE_DEDUCT, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AiBudgetConfigService budgetConfigService;

    @Resource
    private AiBudgetUsageService budgetUsageService;

    @Resource
    private AiBudgetLogService budgetLogService;

    /**
     * 预扣费（调用模型前调用）
     *
     * @param tenantId       租户编号
     * @param userId         用户编号
     * @param estimatedCost  估算费用（微元）
     * @return 预扣凭证（用于后续修正/释放）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 超预算时抛出 BUDGET_EXCEEDED
     */
    public PreDeductResult preDeduct(Long tenantId, Long userId, long estimatedCost) {
        if (estimatedCost <= 0) {
            return new PreDeductResult(tenantId, userId, 0, getCurrentPeriodStart(userId));
        }

        LocalDateTime periodStart = getCurrentPeriodStart(userId);
        String periodStr = periodStart.format(PERIOD_FORMAT);

        // 租户维度的 periodStart 可能与用户维度不同
        LocalDateTime tenantPeriodStart = getCurrentPeriodStart(0L);
        String tenantPeriodStr = tenantPeriodStart.format(PERIOD_FORMAT);

        // 构建 Redis Key
        String userKey = buildKey(tenantId, userId, periodStr);
        String tenantKey = buildKey(tenantId, 0L, tenantPeriodStr);

        // 查询预算上限（按用户实际配置的周期类型）
        long userLimit = getBudgetLimit(userId);
        long tenantLimit = getBudgetLimit(0L);

        // 冷启动：确保 Redis key 存在
        ensureRedisKey(userKey, userId, periodStart);
        if (!userKey.equals(tenantKey)) {
            ensureRedisKey(tenantKey, 0L, tenantPeriodStart);
        }

        // 执行 Lua 脚本
        List<String> keys = new ArrayList<>(2);
        keys.add(userKey);
        keys.add(tenantKey);
        Long result = stringRedisTemplate.execute(PRE_DEDUCT_SCRIPT, keys,
                String.valueOf(userLimit), String.valueOf(tenantLimit), String.valueOf(estimatedCost));

        if (result != null && result == 1L) {
            // 记录超限拦截事件
            logBlockEvent(userId, periodStart, userLimit);
            throw exception(BUDGET_EXCEEDED);
        }
        if (result != null && result == 2L) {
            // 记录租户级超限拦截事件
            logBlockEvent(0L, tenantPeriodStart, tenantLimit);
            throw exception(BUDGET_EXCEEDED);
        }

        return new PreDeductResult(tenantId, userId, estimatedCost, periodStart);
    }

    /**
     * 修正预扣费（调用模型后调用）
     *
     * 用实际费用替换预扣费用：delta = actualCost - preDeductResult.amount
     * 同时更新 DB 用量，并检查阈值告警
     *
     * 性能说明：settle 会写 Redis + DB 两层。Redis 修正是 O(1)；
     * DB 层通过 SQL 原子累加写入 ai_budget_usage，租户级为热点行。
     * 若高并发下 DB 成为瓶颈，可将 settle 改为异步 MQ 消费，
     * 或租户级用量仅依赖 Redis，定期批量同步到 DB。
     */
    public void settle(PreDeductResult preDeductResult, long actualCost) {
        long delta = actualCost - preDeductResult.amount();
        String periodStr = preDeductResult.periodStart().format(PERIOD_FORMAT);
        String userKey = buildKey(preDeductResult.tenantId(), preDeductResult.userId(), periodStr);

        // 租户维度的 periodStart 可能与用户维度不同（如用户 DAILY、租户 MONTHLY）
        LocalDateTime tenantPeriodStart = getCurrentPeriodStart(0L);
        String tenantPeriodStr = tenantPeriodStart.format(PERIOD_FORMAT);
        String tenantKey = buildKey(preDeductResult.tenantId(), 0L, tenantPeriodStr);

        if (delta != 0) {
            // Redis 修正
            stringRedisTemplate.opsForValue().increment(userKey, delta);
            if (!userKey.equals(tenantKey)) {
                stringRedisTemplate.opsForValue().increment(tenantKey, delta);
            }
        }

        // DB 落库（最终准绳）
        if (actualCost > 0) {
            budgetUsageService.addUsage(preDeductResult.userId(), preDeductResult.periodStart(), actualCost);
            if (preDeductResult.userId() != 0L) {
                budgetUsageService.addUsage(0L, tenantPeriodStart, actualCost);
            }
        }

        // 阈值告警检查
        checkThresholdAlerts(preDeductResult.userId(), preDeductResult.periodStart(), actualCost);
        if (preDeductResult.userId() != 0L) {
            checkThresholdAlerts(0L, tenantPeriodStart, actualCost);
        }
    }

    /**
     * 释放预扣费（调用失败/取消时调用）
     */
    public void release(PreDeductResult preDeductResult) {
        if (preDeductResult.amount() <= 0) {
            return;
        }
        String periodStr = preDeductResult.periodStart().format(PERIOD_FORMAT);
        String userKey = buildKey(preDeductResult.tenantId(), preDeductResult.userId(), periodStr);

        LocalDateTime tenantPeriodStart = getCurrentPeriodStart(0L);
        String tenantPeriodStr = tenantPeriodStart.format(PERIOD_FORMAT);
        String tenantKey = buildKey(preDeductResult.tenantId(), 0L, tenantPeriodStr);

        stringRedisTemplate.opsForValue().increment(userKey, -preDeductResult.amount());
        if (!userKey.equals(tenantKey)) {
            stringRedisTemplate.opsForValue().increment(tenantKey, -preDeductResult.amount());
        }
    }

    // ========== 阈值告警 ==========

    /**
     * 检查是否跨越阈值并记录告警事件
     *
     * 逻辑：扣费前的使用比例 < 阈值 <= 扣费后的使用比例 → 触发告警
     */
    private void checkThresholdAlerts(Long userId, LocalDateTime periodStart, long deltaAmount) {
        try {
            AiBudgetConfigDO config = getEnabledBudgetConfig(userId);
            if (config == null) {
                return;
            }
            long budgetAmount = config.getBudgetAmount();
            if (budgetAmount <= 0) {
                return;
            }

            // 解析阈值配置
            List<Integer> thresholds = parseThresholds(config.getAlertThresholds());
            if (thresholds.isEmpty()) {
                return;
            }

            // 查询当前已用金额
            AiBudgetUsageDO usage = budgetUsageService.getUsage(userId, periodStart);
            long usedAmount = usage != null ? usage.getUsedAmount() : 0L;
            long previousUsed = usedAmount - deltaAmount;

            double currentPercent = (double) usedAmount / budgetAmount * 100;
            double previousPercent = (double) previousUsed / budgetAmount * 100;

            for (int threshold : thresholds) {
                // 本次扣费跨越了该阈值
                if (previousPercent < threshold && currentPercent >= threshold) {
                    AiBudgetLogDO logDO = AiBudgetLogDO.builder()
                            .userId(userId)
                            .eventType(AiBudgetEventTypeEnum.THRESHOLD_ALERT.getType())
                            .periodStartTime(periodStart)
                            .currency("CNY")
                            .budgetAmount(budgetAmount)
                            .usedAmount(usedAmount)
                            .deltaAmount(deltaAmount)
                            .message(StrUtil.format("预算使用达到{}%阈值（已用{}/预算{}微元）",
                                    threshold, usedAmount, budgetAmount))
                            .build();
                    budgetLogService.createBudgetLog(logDO);
                    log.warn("[checkThresholdAlerts][userId({}) 预算使用达到{}%阈值, used={}, budget={}]",
                            userId, threshold, usedAmount, budgetAmount);
                }
            }
        } catch (Exception e) {
            log.error("[checkThresholdAlerts][userId({}) 阈值告警检查失败]", userId, e);
        }
    }

    /**
     * 记录超限拦截事件
     */
    private void logBlockEvent(Long userId, LocalDateTime periodStart, long budgetAmount) {
        try {
            AiBudgetUsageDO usage = budgetUsageService.getUsage(userId, periodStart);
            long usedAmount = usage != null ? usage.getUsedAmount() : 0L;
            AiBudgetLogDO logDO = AiBudgetLogDO.builder()
                    .userId(userId)
                    .eventType(AiBudgetEventTypeEnum.OVER_LIMIT_BLOCK.getType())
                    .periodStartTime(periodStart)
                    .currency("CNY")
                    .budgetAmount(budgetAmount)
                    .usedAmount(usedAmount)
                    .message(StrUtil.format("预算超限拦截（已用{}/预算{}微元）", usedAmount, budgetAmount))
                    .build();
            budgetLogService.createBudgetLog(logDO);
        } catch (Exception e) {
            log.error("[logBlockEvent][userId({}) 记录拦截事件失败]", userId, e);
        }
    }

    /**
     * 解析阈值配置，例如 "[80,90,100]" → [80, 90, 100]
     */
    private List<Integer> parseThresholds(String alertThresholds) {
        if (StrUtil.isBlank(alertThresholds)) {
            return List.of(80, 90, 100); // 默认阈值
        }
        try {
            return JSONUtil.toList(alertThresholds, Integer.class);
        } catch (Exception e) {
            return List.of(80, 90, 100);
        }
    }

    // ========== 内部方法 ==========

    private String buildKey(Long tenantId, Long userId, String periodStr) {
        return KEY_PREFIX + tenantId + ":" + userId + ":" + periodStr;
    }

    /**
     * 获取用户当前周期的开始时间
     *
     * 根据用户实际配置的周期类型决定：DAILY 用当天零点，MONTHLY 用月初
     */
    private LocalDateTime getCurrentPeriodStart(Long userId) {
        AiBudgetConfigDO config = getEnabledBudgetConfig(userId);
        if (config != null && AiBudgetPeriodTypeEnum.DAILY.getType().equals(config.getPeriodType())) {
            return LocalDateTime.now()
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return LocalDateTime.now()
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * 获取预算上限，-1 表示无限制（未配置预算）
     *
     * 优先查 MONTHLY，没有则查 DAILY
     */
    private long getBudgetLimit(Long userId) {
        AiBudgetConfigDO config = getEnabledBudgetConfig(userId);
        if (config == null) {
            return -1L; // 无限制
        }
        return config.getBudgetAmount();
    }

    /**
     * 获取用户启用的预算配置，优先 MONTHLY，没有或禁用则查 DAILY
     */
    private AiBudgetConfigDO getEnabledBudgetConfig(Long userId) {
        AiBudgetConfigDO config = budgetConfigService.getBudgetConfig(userId, AiBudgetPeriodTypeEnum.MONTHLY.getType());
        if (config != null && CommonStatusEnum.ENABLE.getStatus().equals(config.getStatus())) {
            return config;
        }
        config = budgetConfigService.getBudgetConfig(userId, AiBudgetPeriodTypeEnum.DAILY.getType());
        if (config != null && CommonStatusEnum.ENABLE.getStatus().equals(config.getStatus())) {
            return config;
        }
        return null;
    }

    /**
     * 冷启动：Redis key 不存在时从 DB 加载
     */
    private void ensureRedisKey(String key, Long userId, LocalDateTime periodStart) {
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        // 从 DB 加载
        AiBudgetUsageDO usage = budgetUsageService.getUsage(userId, periodStart);
        long usedAmount = usage != null ? usage.getUsedAmount() : 0L;
        // 过期时间：DAILY 2 天，MONTHLY 35 天
        AiBudgetConfigDO config = getEnabledBudgetConfig(userId);
        Duration ttl = (config != null && AiBudgetPeriodTypeEnum.DAILY.getType().equals(config.getPeriodType()))
                ? Duration.ofDays(2) : Duration.ofDays(35);
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(usedAmount), ttl);
    }

    /**
     * 预扣凭证
     */
    public record PreDeductResult(Long tenantId, Long userId, long amount, LocalDateTime periodStart) {
    }

}
