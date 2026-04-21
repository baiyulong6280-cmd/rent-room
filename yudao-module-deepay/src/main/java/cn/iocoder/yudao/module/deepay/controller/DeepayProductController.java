package cn.iocoder.yudao.module.deepay.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.deepay.agent.Context;
import cn.iocoder.yudao.module.deepay.orchestrator.ChainOrchestrator;
import cn.iocoder.yudao.module.deepay.orchestrator.ProductionOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Deepay 商品生成接口。
 *
 * <p>
 * POST /api/create-product    —— 轻量版（链码 + IBAN，原有接口）<br>
 * POST /api/produce           —— 完整版（11 Agent 全流程）
 * </p>
 */
@Tag(name = "Deepay - 商品生成")
@RestController
@RequestMapping("/api")
@Validated
public class DeepayProductController {

    @Resource
    private ChainOrchestrator chainOrchestrator;

    @Resource
    private ProductionOrchestrator productionOrchestrator;

    // ----------------------------------------------------------------
    // 旧接口（保持兼容）
    // ----------------------------------------------------------------

    @PostMapping("/create-product")
    @Operation(summary = "一句话生成商品（含链码+支付IBAN）")
    public CommonResult<Map<String, String>> createProduct(@Valid @RequestBody ReqVO reqVO) {
        Context ctx = chainOrchestrator.run(reqVO.getPrompt());

        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("chainCode", ctx.chainCode);
        resp.put("image", ctx.selectedImage);
        resp.put("iban", ctx.iban);
        resp.put("link", "https://deepay.link/" + ctx.chainCode);
        return success(resp);
    }

    // ----------------------------------------------------------------
    // 完整生产流水线（11 Agent Orchestrator）
    // ----------------------------------------------------------------

    @PostMapping("/produce")
    @Operation(summary = "完整生产流水线（找爆款→改款→评估→决策→打版→商品→定价→上架→收款→库存→复盘）")
    public CommonResult<Map<String, Object>> produce(@Valid @RequestBody ReqVO reqVO) {
        Context ctx = productionOrchestrator.run(reqVO.getPrompt());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("chainCode",       ctx.chainCode);
        resp.put("trendKeyword",    ctx.trendKeyword);
        resp.put("patternCode",     ctx.patternCode);
        resp.put("image",           ctx.selectedImage);
        resp.put("price",           ctx.price != null ? String.format("%.2f", ctx.price / 100.0) + " 元" : null);
        resp.put("initialStock",    ctx.initialStock);
        resp.put("publishStatus",   ctx.publishStatus);
        resp.put("jeepayLink",      ctx.jeepayLink);
        resp.put("swanLink",        ctx.swanLink);
        resp.put("link",            "https://deepay.link/" + ctx.chainCode);
        resp.put("analyticsReport", ctx.analyticsReport);
        return success(resp);
    }

    // ----------------------------------------------------------------
    // 内部请求 VO
    // ----------------------------------------------------------------

    /** 创建商品请求体 */
    public static class ReqVO {

        @NotBlank(message = "prompt 不能为空")
        private String prompt;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

}

