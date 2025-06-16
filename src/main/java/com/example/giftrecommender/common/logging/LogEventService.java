package com.example.giftrecommender.common.logging;

import com.example.giftrecommender.dto.request.LogEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LogEventService {

    private final ApplicationEventPublisher publisher;

    public void log(String logLevel, String loggerName, String message) {
        publisher.publishEvent(
                LogEvent.builder()
                        .traceId(MDC.get("traceId"))
                        .logLevel(logLevel)
                        .loggerName(loggerName)
                        .message(message)
                        .threadName(Thread.currentThread().getName())
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }
}