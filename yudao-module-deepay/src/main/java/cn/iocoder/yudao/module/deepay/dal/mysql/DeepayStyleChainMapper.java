package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Deepay 样式链码 Mapper。
 *
 * <p>继承 {@link BaseMapperX} 获得通用 CRUD 能力（MyBatis-Plus）。</p>
 */
@Mapper
public interface DeepayStyleChainMapper extends BaseMapperX<DeepayStyleChainDO> {

    /**
     * 根据 chainCode 回填 ima 知识库 ID。
     *
     * @param chainCode 链码
     * @param imaKbId   ima 知识库 ID
     */
    default void updateImaKbId(String chainCode, String imaKbId) {
        update(new LambdaUpdateWrapper<DeepayStyleChainDO>()
                .eq(DeepayStyleChainDO::getChainCode, chainCode)
                .set(DeepayStyleChainDO::getImaKbId, imaKbId));
    }

}
