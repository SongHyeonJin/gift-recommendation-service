package com.example.giftrecommender.common.quota;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQuotaManager {

    private static final String KEY = "naver:quota:count";
    private static final int DAILY_LIMIT = 25_000;
    private static final double PERMITS_PER_SECOND = 10.0;

    private final RateLimiter rateLimiter = RateLimiter.create(PERMITS_PER_SECOND);
    private final StringRedisTemplate redisTemplate;

    public boolean canCall() {
        // 초당 호출 제한 먼저
        rateLimiter.acquire();  // blocking: 절대 초과 방지

        // 일일 호출 수 제한 (Redis 저장)
        Long count = redisTemplate.opsForValue().increment(KEY);
        if (count == null) count = 0L;

        if (count >= DAILY_LIMIT) {
            log.warn("네이버 API 호출 초과: {}/{}", count, DAILY_LIMIT);
            return false;
        }

        log.debug("네이버 API 호출: {}/{}", count, DAILY_LIMIT);
        return true;
    }

    // 매일 00시 초기화
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetQuota() {
        redisTemplate.delete(KEY);
        log.info("네이버 API 쿼터 초기화 완료");
    }

    public long getCallCount() {
        String raw = redisTemplate.opsForValue().get(KEY);
        return raw != null ? Long.parseLong(raw) : 0L;
    }
}
