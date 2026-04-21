package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeepayStyleChainMapper extends BaseMapperX<DeepayStyleChainDO> {

    default DeepayStyleChainDO selectByChainCode(String chainCode) {
        return selectOne(new LambdaQueryWrapper<DeepayStyleChainDO>()
                .eq(DeepayStyleChainDO::getChainCode, chainCode));
    }

    default void updateImaKbId(String chainCode, String imaKbId) {
        update(null, new LambdaUpdateWrapper<DeepayStyleChainDO>()
                .eq(DeepayStyleChainDO::getChainCode, chainCode)
                .set(DeepayStyleChainDO::getImaKbId, imaKbId));
    }

}


