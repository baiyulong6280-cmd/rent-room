package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.service.FluxService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 设计生成 Agent。
 *
 * <p>职责：根据用户的 prompt，通过 {@link FluxService} 调用 AI 生图服务，
 * 将候选图片列表写入 {@link Context#images}。</p>
 *
 * <p>Agent 本身不含任何 HTTP 细节，所有 AI 交互均委托给 Service 层，
 * 替换底层生图能力时无需改动本类或 Orchestrator。</p>
 */
@Component
public class DesignAgent implements Agent {

    @Resource
    private FluxService fluxService;

    @Override
    public Context run(Context ctx) {
        ctx.images = fluxService.generateImages(ctx.prompt);
        return ctx;
    }

}
