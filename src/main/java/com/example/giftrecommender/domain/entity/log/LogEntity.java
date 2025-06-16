package com.example.giftrecommender.domain.entity.log;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String traceId;
    private String logLevel;
    private String loggerName;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String threadName;

    private LocalDateTime createdAt;

    @Builder
    public LogEntity(String traceId, String logLevel, String loggerName,
                     String message, String threadName, LocalDateTime createdAt) {
        this.traceId = traceId;
        this.logLevel = logLevel;
        this.loggerName = loggerName;
        this.message = message;
        this.threadName = threadName;
        this.createdAt = createdAt;
    }
}
