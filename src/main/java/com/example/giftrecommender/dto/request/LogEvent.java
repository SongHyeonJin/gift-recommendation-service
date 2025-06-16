package com.example.giftrecommender.dto.request;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record LogEvent(
        String traceId,
        String logLevel,
        String loggerName,
        String message,
        String threadName,
        LocalDateTime createdAt
) {}
