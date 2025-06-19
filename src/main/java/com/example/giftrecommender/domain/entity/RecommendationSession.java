package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSession {

    @Id
    @Column(name = "recommendation_session_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    private Instant createdAt;
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Builder
    public RecommendationSession(UUID id, Guest guest, SessionStatus status) {
        this.id = id;
        this.guest = guest;
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

}
