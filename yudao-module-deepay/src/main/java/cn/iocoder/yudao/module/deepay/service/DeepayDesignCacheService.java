package cn.iocoder.yudao.module.deepay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设计出图 Redis 缓存服务（STEP 22）。
 *
 * <p>缓存 key 包含全部影响结果的维度：
 * {@code design:{category}:{style}:{market}:{priceRange}}
 * 以避免不同市场/价格带的用户得到错误的推荐款式。</p>
 *
 * <p>TTL 默认 1 小时。</p>
 */
@Service
public class DeepayDesignCacheService {

    private static final Logger log = LoggerFactory.getLogger(DeepayDesignCacheService.class);

    private static final String KEY_PREFIX = "design:";
    private static final long   TTL_HOURS  = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 构建缓存 key。
     * 四维度：category + style + market + priceRange，空值用 "_" 占位。
     */
    public String buildKey(String category, String style, String market, String priceRange) {
        return KEY_PREFIX
                + safe(category)  + ":"
                + safe(style)     + ":"
                + safe(market)    + ":"
                + safe(priceRange);
    }

    /**
     * 从缓存读取 safeImages 列表。
     *
     * @return 缓存命中时返回列表；否则返回 null
     */
    public List<String> get(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) return null;
            List<String> result = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            log.info("[DesignCache] 缓存命中 key={} size={}", key, result.size());
            return result;
        } catch (Exception e) {
            log.warn("[DesignCache] 读取缓存失败 key={}", key, e);
            return null;
        }
    }

    /**
     * 将 safeImages 写入缓存，TTL = 1 小时。
     */
    public void set(String key, List<String> images) {
        if (images == null || images.isEmpty()) return;
        try {
            String json = MAPPER.writeValueAsString(images);
            stringRedisTemplate.opsForValue().set(key, json, TTL_HOURS, TimeUnit.HOURS);
            log.info("[DesignCache] 已写入缓存 key={} size={}", key, images.size());
        } catch (Exception e) {
            log.warn("[DesignCache] 写入缓存失败 key={}", key, e);
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "_";
    }

}
