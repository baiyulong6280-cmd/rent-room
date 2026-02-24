package cn.iocoder.yudao.module.ai.service.billing;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetConfigDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
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
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

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
            return new PreDeductResult(tenantId, userId, 0, getCurrentPeriodStart());
        }

        LocalDateTime periodStart = getCurrentPeriodStart();
        String periodStr = periodStart.format(PERIOD_FORMAT);

        // 构建 Redis Key
        String userKey = buildKey(tenantId, userId, periodStr);
        String tenantKey = buildKey(tenantId, 0L, periodStr);

        // 查询预算上限
        long userLimit = getBudgetLimit(userId, AiBudgetPeriodTypeEnum.MONTHLY.getType());
        long tenantLimit = getBudgetLimit(0L, AiBudgetPeriodTypeEnum.MONTHLY.getType());

        // 冷启动：确保 Redis key 存在
        ensureRedisKey(userKey, userId, periodStart);
        if (!userKey.equals(tenantKey)) {
            ensureRedisKey(tenantKey, 0L, periodStart);
        }

        // 执行 Lua 脚本
        List<String> keys = new ArrayList<>(2);
        keys.add(userKey);
        keys.add(tenantKey);
        Long result = stringRedisTemplate.execute(PRE_DEDUCT_SCRIPT, keys,
                String.valueOf(userLimit), String.valueOf(tenantLimit), String.valueOf(estimatedCost));

        if (result != null && result == 1L) {
            throw exception(BUDGET_EXCEEDED);
        }
        if (result != null && result == 2L) {
            throw exception(BUDGET_EXCEEDED);
        }

        return new PreDeductResult(tenantId, userId, estimatedCost, periodStart);
    }

    /**
     * 修正预扣费（调用模型后调用）
     *
     * 用实际费用替换预扣费用：delta = actualCost - preDeductResult.amount
     * - delta > 0：再扣差额
     * - delta < 0：退回差额
     * - delta = 0：无需操作
     *
     * 同时更新 DB 用量
     */
    public void settle(PreDeductResult preDeductResult, long actualCost) {
        long delta = actualCost - preDeductResult.amount();
        String periodStr = preDeductResult.periodStart().format(PERIOD_FORMAT);
        String userKey = buildKey(preDeductResult.tenantId(), preDeductResult.userId(), periodStr);
        String tenantKey = buildKey(preDeductResult.tenantId(), 0L, periodStr);

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
                budgetUsageService.addUsage(0L, preDeductResult.periodStart(), actualCost);
            }
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
        String tenantKey = buildKey(preDeductResult.tenantId(), 0L, periodStr);

        stringRedisTemplate.opsForValue().increment(userKey, -preDeductResult.amount());
        if (!userKey.equals(tenantKey)) {
            stringRedisTemplate.opsForValue().increment(tenantKey, -preDeductResult.amount());
        }
    }

    // ========== 内部方法 ==========

    private String buildKey(Long tenantId, Long userId, String periodStr) {
        return KEY_PREFIX + tenantId + ":" + userId + ":" + periodStr;
    }

    private LocalDateTime getCurrentPeriodStart() {
        return LocalDateTime.now()
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * 获取预算上限，-1 表示无限制（未配置预算）
     */
    private long getBudgetLimit(Long userId, String periodType) {
        AiBudgetConfigDO config = budgetConfigService.getBudgetConfig(userId, periodType);
        if (config == null || !CommonStatusEnum.ENABLE.getStatus().equals(config.getStatus())) {
            return -1L; // 无限制
        }
        return config.getBudgetAmount();
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
        // 设置到 Redis，过期时间 35 天（覆盖一个月周期 + 缓冲）
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(usedAmount), Duration.ofDays(35));
    }

    /**
     * 预扣凭证
     */
    public record PreDeductResult(Long tenantId, Long userId, long amount, LocalDateTime periodStart) {
    }

}
