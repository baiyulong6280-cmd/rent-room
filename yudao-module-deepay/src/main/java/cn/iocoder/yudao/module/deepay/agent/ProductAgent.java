package cn.iocoder.yudao.module.deepay.agent;

import org.springframework.stereotype.Component;

/**
 * ProductAgent — 生成商品信息（title / description）。
 */
@Component
public class ProductAgent implements Agent {

    @Override
    public Context run(Context ctx) {
        ctx.title       = "羊绒大衣";
        ctx.description = "高端款";
        return ctx;
    }

}

