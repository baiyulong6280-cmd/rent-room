package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeepayInventoryMapper extends BaseMapperX<DeepayInventoryDO> {

    default DeepayInventoryDO selectByChainCode(String chainCode) {
        return selectOne(new LambdaQueryWrapper<DeepayInventoryDO>()
                .eq(DeepayInventoryDO::getChainCode, chainCode));
    }

}
