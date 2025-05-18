package com.example.giftrecommender.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest {

    @Id
    @Column(name = "guest_id")
    private UUID id;

    // 생성 시간 (UTC)
    private Instant createdAt;

    // 마지막 접속 시간 (UTC)
    private Instant lastAccessedAt;

    @Builder
    public Guest(UUID id) {
        this.id = id;
    }

    // 최초 저장 시 자동 세팅
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastAccessedAt = now;
    }

}
