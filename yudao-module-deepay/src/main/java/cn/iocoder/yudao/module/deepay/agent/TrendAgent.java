package cn.iocoder.yudao.module.deepay.agent;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * TrendAgent — 找爆款，返回参考图列表。
 */
@Component
public class TrendAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        ctx.referenceImages = Arrays.asList(
                "https://img1",
                "https://img2",
                "https://img3"
        );
        return ctx;
    }

}

