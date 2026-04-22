package cn.iocoder.yudao.module.deepay.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.deepay.dal.dataobject.DeepayDesignImageDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 设计图评分 Mapper（ImageScoringAgent 写入 / FeedbackAgent 更新）。
 */
@Mapper
public interface DeepayDesignImageMapper extends BaseMapperX<DeepayDesignImageDO> {

    /** 查询指定品类的 Top-N 图片（按综合分降序）。 */
    default List<DeepayDesignImageDO> selectTopByCategory(String category, int limit) {
        return selectList(new LambdaQueryWrapper<DeepayDesignImageDO>()
                .eq(category != null, DeepayDesignImageDO::getCategory, category)
                .orderByDesc(DeepayDesignImageDO::getScore)
                .last("LIMIT " + limit));
    }

}
