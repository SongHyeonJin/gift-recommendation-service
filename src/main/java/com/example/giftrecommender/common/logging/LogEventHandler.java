package com.example.giftrecommender.common.logging;

import com.example.giftrecommender.domain.entity.log.LogEntity;
import com.example.giftrecommender.domain.repository.log.LogRepository;
import com.example.giftrecommender.dto.request.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventHandler {

    private final LogRepository logRepository;
    private static final List<String> EXCLUDE_URLS = List.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui/",
            "/swagger-ui/index.html"
    );

    @Async
    @EventListener
    public void saveLog(LogEvent event) {
        try {
            // Swagger 관련 요청 로그는 저장하지 않음
            boolean isExcluded = EXCLUDE_URLS.stream().anyMatch(event.message()::contains);
            if (isExcluded) return;

            logRepository.save(
                    LogEntity.builder()
                            .traceId(event.traceId())
                            .logLevel(event.logLevel())
                            .loggerName(event.loggerName())
                            .message(event.message())
                            .threadName(event.threadName())
                            .createdAt(event.createdAt())
                            .build()
            );
        } catch (Exception e) {
            log.error("로그 DB 저장 실패", e);
        }
    }
}
