package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED
)
public class RecommendationSession {

    @Id
    @Column(name = "recommendation_session_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Column(nullable = false)
    private String name;

    private LocalDateTime createdAt;
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Builder
    public RecommendationSession(UUID id, Guest guest, String name, SessionStatus status) {
        this.id = id;
        this.guest = guest;
        this.name = name;
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
