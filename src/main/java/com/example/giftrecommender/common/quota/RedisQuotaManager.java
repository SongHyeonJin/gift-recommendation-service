package com.example.giftrecommender.common.quota;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQuotaManager {

    private static final String DAILY_KEY = "naver:quota:count";
    private static final int DAILY_LIMIT = 25_000;
    private static final int SECOND_LIMIT = 9;
    private static final String SECOND_KEY_PREFIX = "naver:quota:second:";
    private static final int MAX_RETRY = 10;
    private static final int WAIT_MILLIS = 300;

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
        local secondCount = tonumber(redis.call('GET', KEYS[1]) or '0')
        if secondCount >= tonumber(ARGV[2]) then
            return -1
        end
    
        local dayCount = tonumber(redis.call('GET', KEYS[2]) or '0')
        if dayCount >= tonumber(ARGV[3]) then
            return -2
        end
    
        local newSecondCount = redis.call('INCR', KEYS[1])
        if newSecondCount == 1 then
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
        end
    
        redis.call('INCR', KEYS[2])
        return newSecondCount
    """;
    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    /*
     * 초당 호출 수가 허용될 때까지 대기하며 획득
     * 실패 시 RuntimeException 발생
     */
    public void acquire() {
        String secondKey = SECOND_KEY_PREFIX + currentSecond();
        List<String> keys = List.of(secondKey, DAILY_KEY);

        for (int i = 0; i < MAX_RETRY; i++) {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    keys,
                    String.valueOf(2),
                    String.valueOf(SECOND_LIMIT),
                    String.valueOf(DAILY_LIMIT)
            );

            if (result == null) {
                log.warn("Redis 응답 없음");
            } else if (result == -1L) {
                log.debug("초당 호출 초과 재시도 중... {}/{}", i + 1, MAX_RETRY);
            } else if (result == -2L) {
                log.warn("일일 호출 초과");
                throw new ErrorException(ExceptionEnum.QUOTA_DAILY_EXCEEDED);
            } else {
                log.info("쿼터 허용됨. 현재 일일 호출 수 증가: {}", result);
                return;
            }

            try {
                Thread.sleep(WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("쿼터 대기 중 인터럽트 발생", e);
            }
        }

        throw new ErrorException(ExceptionEnum.QUOTA_SECOND_EXCEEDED);
    }

    private String currentSecond() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    // 매일 00시 일일 카운터 초기화
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetQuota() {
        redisTemplate.delete(DAILY_KEY);
        log.info("네이버 API 일일 쿼터 초기화 완료");
    }

}
