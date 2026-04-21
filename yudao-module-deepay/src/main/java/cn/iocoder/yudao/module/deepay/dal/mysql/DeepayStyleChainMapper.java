package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayStyleChainDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Deepay 样式链码 Mapper。
 *
 * <p>继承 {@link BaseMapperX} 获得通用 CRUD 能力（MyBatis-Plus）。</p>
 */
@Mapper
public interface DeepayStyleChainMapper extends BaseMapperX<DeepayStyleChainDO> {

}
