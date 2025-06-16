package com.example.giftrecommender.common.logging;

import com.example.giftrecommender.domain.entity.log.LogEntity;
import com.example.giftrecommender.domain.repository.log.LogRepository;
import com.example.giftrecommender.dto.request.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventHandler {

    private final LogRepository logRepository;

    @Async
    @EventListener
    public void saveLog(LogEvent event) {
        try {
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
