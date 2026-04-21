package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayOrderDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface DeepayOrderMapper extends BaseMapperX<DeepayOrderDO> {

    default DeepayOrderDO selectByPaymentId(String paymentId) {
        return selectOne(new LambdaQueryWrapper<DeepayOrderDO>()
                .eq(DeepayOrderDO::getPaymentId, paymentId));
    }

    default void markPaid(String paymentId) {
        update(null, new LambdaUpdateWrapper<DeepayOrderDO>()
                .eq(DeepayOrderDO::getPaymentId, paymentId)
                .set(DeepayOrderDO::getStatus, "PAID")
                .set(DeepayOrderDO::getPaidAt, LocalDateTime.now()));
    }

}
