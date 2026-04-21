package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.service.FluxService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * DesignAgent — 根据关键词生成改款图。
 * 调用 FluxService（已有 AI 生图 + 保底图降级）。
 */
@Component
public class DesignAgent implements Agent {

    @Resource
    private FluxService fluxService;

    @Override
    public Context run(Context ctx) {
        String keyword = ctx.keyword != null ? ctx.keyword : "";
        ctx.designImages = fluxService.generateImages(keyword);
        return ctx;
    }

}

