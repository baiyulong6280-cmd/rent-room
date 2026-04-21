package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayInventoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Deepay 库存 Mapper。
 *
 * <p>继承 {@link BaseMapperX} 获得通用 CRUD 能力（MyBatis-Plus）。</p>
 */
@Mapper
public interface DeepayInventoryMapper extends BaseMapperX<DeepayInventoryDO> {

    /**
     * 根据 chainCode 查询库存记录。
     *
     * @param chainCode 商品链码
     * @return 库存记录，若不存在则返回 null
     */
    default DeepayInventoryDO selectByChainCode(String chainCode) {
        return selectOne(new LambdaQueryWrapper<DeepayInventoryDO>()
                .eq(DeepayInventoryDO::getChainCode, chainCode));
    }

    /**
     * 查询所有库存记录。
     *
     * @return 所有库存列表
     */
    default List<DeepayInventoryDO> selectAll() {
        return selectList(new LambdaQueryWrapper<>());
    }

}
