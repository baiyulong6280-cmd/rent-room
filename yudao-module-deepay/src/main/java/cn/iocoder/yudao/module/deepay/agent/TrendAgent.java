package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.service.TrendService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * TrendAgent — 获取趋势参考图列表。
 *
 * <p>委托 {@link TrendService} 完成实际查询，优先级：
 * 外部趋势 API → 系统内近 7 天热销图 → 内置 fallback。</p>
 */
@Component
public class TrendAgent implements Agent {

    @Resource
    private TrendService trendService;

    @Override
    public Context run(Context ctx) {
        ctx.referenceImages = trendService.getReferenceImages(ctx.keyword);
        return ctx;
    }

}

