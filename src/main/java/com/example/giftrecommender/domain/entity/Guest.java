package com.example.giftrecommender.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest {

    @Id
    private UUID id;

    // 생성 시간
    private LocalDateTime createdAt;

    // 마지막 접속 시간
    private LocalDateTime lastAccessedAt;

    @Builder
    public Guest(UUID id) {
        this.id = id;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = this.createdAt;
    }

}
