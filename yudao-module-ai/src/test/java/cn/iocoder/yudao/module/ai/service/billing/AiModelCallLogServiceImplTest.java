package cn.iocoder.yudao.module.ai.service.billing;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.ai.controller.admin.model.vo.calllog.AiModelCallLogStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.model.vo.calllog.AiModelCallLogStatRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiModelCallLogDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.billing.AiModelPricingDO;
import cn.iocoder.yudao.module.ai.dal.mysql.billing.AiModelCallLogMapper;
import cn.iocoder.yudao.module.ai.enums.billing.AiCallStatusEnum;
import cn.iocoder.yudao.module.ai.enums.billing.AiTokenSourceEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AiModelCallLogServiceImpl} 的单元测试
 */
@Import(AiModelCallLogServiceImpl.class)
public class AiModelCallLogServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AiModelCallLogServiceImpl callLogService;

    @Resource
    private AiModelCallLogMapper callLogMapper;

    @MockitoBean
    private AiModelPricingService modelPricingService;

    @Test
    public void testCreateCallLog_withPricing() {
        // 准备计费配置
        AiModelPricingDO pricing = new AiModelPricingDO();
        pricing.setPriceInPer1m(2_000_000L);
        pricing.setPriceCachedPer1m(0L);
        pricing.setPriceOutPer1m(8_000_000L);
        pricing.setPriceReasoningPer1m(0L);
        when(modelPricingService.getLatestModelPricing(eq(100L))).thenReturn(pricing);

        // 准备调用日志
        AiModelCallLogDO callLog = AiModelCallLogDO.builder()
                .userId(1L)
                .platform("DEEP_SEEK")
                .modelId(100L)
                .model("deepseek-chat")
                .bizType("CHAT_MESSAGE")
                .bizId(200L)
                .requestTime(LocalDateTime.now())
                .responseTime(LocalDateTime.now())
                .durationMs(1500)
                .status(AiCallStatusEnum.SUCCESS.getStatus())
                .promptTokens(1000)
                .completionTokens(500)
                .tokenSource(AiTokenSourceEnum.PROVIDER.getSource())
                .build();

        // 调用
        Long id = callLogService.createCallLog(callLog);

        // 校验
        assertNotNull(id);
        AiModelCallLogDO dbLog = callLogMapper.selectById(id);
        assertNotNull(dbLog);
        // 验证价格快照
        assertEquals(2_000_000L, dbLog.getPriceInPer1m());
        assertEquals(8_000_000L, dbLog.getPriceOutPer1m());
        // 验证费用计算: 1000 * 2 + 500 * 8 = 6000 微元
        assertEquals(6000L, dbLog.getCostAmount());
        assertEquals("CNY", dbLog.getCurrency());
        assertFalse(dbLog.getBlocked());
    }

    @Test
    public void testCreateCallLog_noPricing() {
        // 无计费配置
        when(modelPricingService.getLatestModelPricing(eq(100L))).thenReturn(null);

        AiModelCallLogDO callLog = AiModelCallLogDO.builder()
                .userId(1L)
                .platform("OPENAI")
                .modelId(100L)
                .model("gpt-4o")
                .bizType("CHAT_MESSAGE")
                .bizId(200L)
                .requestTime(LocalDateTime.now())
                .responseTime(LocalDateTime.now())
                .durationMs(800)
                .status(AiCallStatusEnum.SUCCESS.getStatus())
                .promptTokens(500)
                .completionTokens(200)
                .tokenSource(AiTokenSourceEnum.PROVIDER.getSource())
                .build();

        Long id = callLogService.createCallLog(callLog);

        AiModelCallLogDO dbLog = callLogMapper.selectById(id);
        // 无计费配置时，单价和费用均为 0
        assertEquals(0L, dbLog.getPriceInPer1m());
        assertEquals(0L, dbLog.getPriceOutPer1m());
        assertEquals(0L, dbLog.getCostAmount());
    }

    @Test
    public void testCreateCallLog_failWithNoTokens() {
        AiModelPricingDO pricing = new AiModelPricingDO();
        pricing.setPriceInPer1m(2_000_000L);
        pricing.setPriceCachedPer1m(0L);
        pricing.setPriceOutPer1m(8_000_000L);
        pricing.setPriceReasoningPer1m(0L);
        when(modelPricingService.getLatestModelPricing(eq(100L))).thenReturn(pricing);

        AiModelCallLogDO callLog = AiModelCallLogDO.builder()
                .userId(1L)
                .platform("DEEP_SEEK")
                .modelId(100L)
                .model("deepseek-chat")
                .bizType("CHAT_MESSAGE")
                .bizId(200L)
                .requestTime(LocalDateTime.now())
                .responseTime(LocalDateTime.now())
                .durationMs(100)
                .status(AiCallStatusEnum.FAIL.getStatus())
                .errorMessage("Connection timeout")
                .tokenSource(AiTokenSourceEnum.NONE.getSource())
                .build();

        Long id = callLogService.createCallLog(callLog);

        AiModelCallLogDO dbLog = callLogMapper.selectById(id);
        // 失败且无 token 时费用为 0
        assertEquals(0L, dbLog.getCostAmount());
    }

    @Test
    public void testGetCallLogStat_empty() {
        AiModelCallLogStatReqVO reqVO = new AiModelCallLogStatReqVO();
        AiModelCallLogStatRespVO stat = callLogService.getCallLogStat(reqVO);

        assertEquals(0L, stat.getTotalCount());
        assertEquals(0L, stat.getSuccessCount());
        assertEquals(0L, stat.getFailCount());
        assertEquals(0L, stat.getTotalPromptTokens());
        assertEquals(0L, stat.getTotalCostAmount());
    }

    @Test
    public void testGetCallLogStat_withData() {
        when(modelPricingService.getLatestModelPricing(eq(100L))).thenReturn(null);

        // 插入两条日志
        AiModelCallLogDO log1 = AiModelCallLogDO.builder()
                .userId(1L).platform("DEEP_SEEK").modelId(100L).model("deepseek-chat")
                .bizType("CHAT_MESSAGE").bizId(1L)
                .requestTime(LocalDateTime.now()).responseTime(LocalDateTime.now())
                .durationMs(1000).status(AiCallStatusEnum.SUCCESS.getStatus())
                .promptTokens(500).completionTokens(200).totalTokens(700)
                .tokenSource(AiTokenSourceEnum.PROVIDER.getSource())
                .build();
        callLogService.createCallLog(log1);

        AiModelCallLogDO log2 = AiModelCallLogDO.builder()
                .userId(1L).platform("DEEP_SEEK").modelId(100L).model("deepseek-chat")
                .bizType("CHAT_MESSAGE").bizId(2L)
                .requestTime(LocalDateTime.now()).responseTime(LocalDateTime.now())
                .durationMs(2000).status(AiCallStatusEnum.FAIL.getStatus())
                .errorMessage("error")
                .tokenSource(AiTokenSourceEnum.NONE.getSource())
                .build();
        callLogService.createCallLog(log2);

        AiModelCallLogStatReqVO reqVO = new AiModelCallLogStatReqVO();
        AiModelCallLogStatRespVO stat = callLogService.getCallLogStat(reqVO);

        assertEquals(2L, stat.getTotalCount());
        assertEquals(1L, stat.getSuccessCount());
        assertEquals(1L, stat.getFailCount());
        assertEquals(500L, stat.getTotalPromptTokens());
        assertEquals(200L, stat.getTotalCompletionTokens());
    }

}
