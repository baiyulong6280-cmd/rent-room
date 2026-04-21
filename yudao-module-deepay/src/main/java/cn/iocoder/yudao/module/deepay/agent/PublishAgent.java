package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 上架 Agent（PublishAgent）。
 *
 * <p>职责：将商品状态更新为 {@code PUBLISHED}，完成上架动作，
 * 并把上架状态写入 {@link Context#publishStatus}。</p>
 *
 * <p>该 Agent 依赖 PricingAgent 和 ProductAgent 已完成执行（price 和 chainCode 已写入 ctx）。</p>
 */
@Component
public class PublishAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PublishAgent.class);

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Override
    public Context run(Context ctx) {
        if (ctx.chainCode == null) {
            throw new IllegalStateException("PublishAgent: chainCode 为空，无法执行上架");
        }

        deepayStyleChainMapper.update(null,
                new LambdaUpdateWrapper<DeepayStyleChainDO>()
                        .eq(DeepayStyleChainDO::getChainCode, ctx.chainCode)
                        .set(DeepayStyleChainDO::getStatus, STATUS_PUBLISHED));

        ctx.publishStatus = STATUS_PUBLISHED;
        log.info("PublishAgent: 商品已上架，chainCode={}", ctx.chainCode);
        return ctx;
    }

}

