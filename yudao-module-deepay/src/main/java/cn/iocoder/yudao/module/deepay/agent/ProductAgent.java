package cn.iocoder.yudao.module.deepay.agent;

import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import cn.iocoder.yudao.module.deepay.dal.mysql.DeepayStyleChainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 商品生成 Agent（ProductAgent）。
 *
 * <p>职责：将打版编码、趋势关键词等信息写回 {@code deepay_style_chain} 记录，
 * 把记录 ID 存入 {@link Context#productRecordId}，作为后续定价、上架的依据。</p>
 *
 * <p>注意：MVP 阶段商品信息存储在 deepay 模块自身的链码表中，
 * 后续可通过调用 product 模块 API 创建真实 SPU。</p>
 */
@Component
public class ProductAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ProductAgent.class);

    @Resource
    private DeepayStyleChainMapper deepayStyleChainMapper;

    @Override
    public Context run(Context ctx) {
        if (ctx.chainCode == null) {
            throw new IllegalStateException("ProductAgent: chainCode 为空，无法更新商品记录");
        }

        // 将打版编码和趋势关键词写回链码记录
        deepayStyleChainMapper.updatePatternAndTrend(ctx.chainCode, ctx.patternCode, ctx.trendKeyword);

        // 查出记录 ID 存入 ctx
        DeepayStyleChainDO record = deepayStyleChainMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeepayStyleChainDO>()
                        .eq(DeepayStyleChainDO::getChainCode, ctx.chainCode));
        if (record != null) {
            ctx.productRecordId = record.getId();
        }
        log.info("ProductAgent: 商品记录更新完成，chainCode={} productRecordId={}", ctx.chainCode, ctx.productRecordId);
        return ctx;
    }

}
