package cn.iocoder.yudao.module.deepay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * 限流服务（STEP 23）。
 *
 * <p>使用 Redis INCR + EXPIRE 实现滑动窗口限流：
 * 同一用户 1 分钟内最多允许 {@value #MAX_PER_MINUTE} 次出图请求。</p>
 *
 * <p>key 格式：{@code rate:design:{userId}}</p>
 */
@Slf4j
@Service
public class DeepayRateLimitService {

    private static final int    MAX_PER_MINUTE = 3;
    private static final String KEY_PREFIX     = "rate:design:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 判断本次请求是否被允许。
     *
     * <p>首次 INCR 时（count == 1）设置 1 分钟过期，后续在同一时间窗口内计数。
     * 超过上限时返回 false，调用方应拒绝请求。</p>
     *
     * @param userId 用户 ID（允许为 "anonymous"）
     * @return true 表示允许，false 表示超限
     */
    public boolean allow(String userId) {
        String key = KEY_PREFIX + (userId != null ? userId : "anonymous");
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次计数，设置窗口过期时间
                stringRedisTemplate.expire(key, Duration.ofMinutes(1));
            }
            boolean allowed = count != null && count <= MAX_PER_MINUTE;
            if (!allowed) {
                log.warn("[RateLimit] 超出限流 userId={} count={} max={}", userId, count, MAX_PER_MINUTE);
            }
            return allowed;
        } catch (Exception e) {
            // Redis 异常时放行，避免误伤正常用户
            log.warn("[RateLimit] Redis 异常，放行请求 userId={}", userId, e);
            return true;
        }
    }

}
