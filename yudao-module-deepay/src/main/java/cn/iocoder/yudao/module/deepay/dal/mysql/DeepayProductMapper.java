package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayProductDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeepayProductMapper extends BaseMapperX<DeepayProductDO> {

    default List<DeepayProductDO> selectBySelling() {
        return selectList(new LambdaQueryWrapper<DeepayProductDO>()
                .eq(DeepayProductDO::getStatus, "SELLING"));
    }

    default void addStock(Long id, int delta) {
        update(null, new LambdaUpdateWrapper<DeepayProductDO>()
                .setSql("stock = stock + " + delta)
                .eq(DeepayProductDO::getId, id));
    }

    default void incrementSoldCount(Long id) {
        update(null, new LambdaUpdateWrapper<DeepayProductDO>()
                .setSql("sold_count = sold_count + 1, stock = stock - 1")
                .eq(DeepayProductDO::getId, id)
                .gt(DeepayProductDO::getStock, 0));
    }

    default void updateStatus(Long id, String status) {
        update(null, new LambdaUpdateWrapper<DeepayProductDO>()
                .set(DeepayProductDO::getStatus, status)
                .eq(DeepayProductDO::getId, id));
    }

}
