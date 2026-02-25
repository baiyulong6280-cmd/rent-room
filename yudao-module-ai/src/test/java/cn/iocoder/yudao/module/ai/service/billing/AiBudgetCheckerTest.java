package cn.iocoder.yudao.module.ai.service.billing;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetConfigDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetLogDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiBudgetUsageDO;
import cn.iocoder.yudao.module.ai.enums.billing.AiBudgetPeriodTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiBudgetChecker} 的单元测试
 */
public class AiBudgetCheckerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AiBudgetChecker budgetChecker;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private AiBudgetConfigService budgetConfigService;

    @Mock
    private AiBudgetUsageService budgetUsageService;

    @Mock
    private AiBudgetLogService budgetLogService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(budgetUsageService.getUsage(anyLong(), any())).thenReturn(null);
    }

    // ========== preDeduct ==========

    @Test
    public void testPreDeduct_zeroCost() {
        AiBudgetChecker.PreDeductResult result = budgetChecker.preDeduct(1L, 100L, 0);
        assertEquals(0, result.amount());
        assertEquals(1L, result.tenantId());
        assertEquals(100L, result.userId());
    }

    @Test
    public void testPreDeduct_noBudgetConfig_unlimited() {
        // 无预算配置 → 无限制（-1）
        lenient().when(budgetConfigService.getBudgetConfig(anyLong(), anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        // Lua 脚本返回 0（成功）
        lenient().when(stringRedisTemplate.execute(Mockito.<RedisScript<Long>>any(), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(0L);

        AiBudgetChecker.PreDeductResult result = budgetChecker.preDeduct(1L, 100L, 5000L);
        assertEquals(5000L, result.amount());
    }

    @Test
    public void testPreDeduct_userBudgetExceeded() {
        // 用户预算超限
        AiBudgetConfigDO userConfig = AiBudgetConfigDO.builder()
                .userId(100L).periodType("MONTHLY").budgetAmount(10_000_000L)
                .status(CommonStatusEnum.ENABLE.getStatus()).build();
        lenient().when(budgetConfigService.getBudgetConfig(anyLong(), anyString())).thenReturn(null);
        when(budgetConfigService.getBudgetConfig(eq(100L), eq(AiBudgetPeriodTypeEnum.MONTHLY.getType())))
                .thenReturn(userConfig);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        // Lua 脚本返回 1（用户预算不足）
        lenient().when(stringRedisTemplate.execute(Mockito.<RedisScript<Long>>any(), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(1L);

        assertThrows(ServiceException.class, () -> budgetChecker.preDeduct(1L, 100L, 5000L));
        // 验证记录了拦截事件
        verify(budgetLogService).createBudgetLog(any(AiBudgetLogDO.class));
    }

    @Test
    public void testPreDeduct_tenantBudgetExceeded() {
        lenient().when(budgetConfigService.getBudgetConfig(anyLong(), anyString())).thenReturn(null);
        AiBudgetConfigDO tenantConfig = AiBudgetConfigDO.builder()
                .userId(0L).periodType("MONTHLY").budgetAmount(50_000_000L)
                .status(CommonStatusEnum.ENABLE.getStatus()).build();
        when(budgetConfigService.getBudgetConfig(eq(0L), eq(AiBudgetPeriodTypeEnum.MONTHLY.getType())))
                .thenReturn(tenantConfig);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        // Lua 脚本返回 2（租户预算不足）
        lenient().when(stringRedisTemplate.execute(Mockito.<RedisScript<Long>>any(), anyList(), any(String.class), any(String.class), any(String.class)))
                .thenReturn(2L);

        assertThrows(ServiceException.class, () -> budgetChecker.preDeduct(1L, 100L, 5000L));
        verify(budgetLogService).createBudgetLog(any(AiBudgetLogDO.class));
    }

    // ========== settle ==========

    @Test
    public void testSettle_actualCostLessThanPreDeduct() {
        lenient().when(budgetConfigService.getBudgetConfig(anyLong(), anyString())).thenReturn(null);

        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        AiBudgetChecker.PreDeductResult preDeduct = new AiBudgetChecker.PreDeductResult(1L, 100L, 10000L, periodStart);

        budgetChecker.settle(preDeduct, 6000L);

        // delta = 6000 - 10000 = -4000（退回差额）
        verify(valueOperations, atLeastOnce()).increment(contains(":100:"), eq(-4000L));
        verify(valueOperations, atLeastOnce()).increment(contains(":0:"), eq(-4000L));
        // DB 落库
        verify(budgetUsageService).addUsage(eq(100L), eq(periodStart), eq(6000L));
        verify(budgetUsageService).addUsage(eq(0L), any(LocalDateTime.class), eq(6000L));
    }

    @Test
    public void testSettle_zeroCost() {
        lenient().when(budgetConfigService.getBudgetConfig(anyLong(), anyString())).thenReturn(null);

        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        AiBudgetChecker.PreDeductResult preDeduct = new AiBudgetChecker.PreDeductResult(1L, 100L, 5000L, periodStart);

        budgetChecker.settle(preDeduct, 0L);

        // delta = 0 - 5000 = -5000
        verify(valueOperations, atLeastOnce()).increment(contains(":100:"), eq(-5000L));
        // 不落库（actualCost = 0）
        verify(budgetUsageService, never()).addUsage(anyLong(), any(), anyLong());
    }

    // ========== release ==========

    @Test
    public void testRelease_zeroAmount() {
        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        AiBudgetChecker.PreDeductResult preDeduct = new AiBudgetChecker.PreDeductResult(1L, 100L, 0, periodStart);

        budgetChecker.release(preDeduct);

        // 不应调用 Redis
        verify(valueOperations, never()).increment(anyString(), anyLong());
    }

    @Test
    public void testRelease_positiveAmount() {
        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        AiBudgetChecker.PreDeductResult preDeduct = new AiBudgetChecker.PreDeductResult(1L, 100L, 8000L, periodStart);

        budgetChecker.release(preDeduct);

        verify(valueOperations).increment(contains(":100:"), eq(-8000L));
        verify(valueOperations).increment(contains(":0:"), eq(-8000L));
    }

}
