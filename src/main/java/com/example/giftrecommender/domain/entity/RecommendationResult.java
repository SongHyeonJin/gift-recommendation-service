package com.example.giftrecommender.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationResult {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_session_id", nullable = false)
    private RecommendationSession recommendationSession;

    private LocalDateTime createdAt;

    @ElementCollection
    @CollectionTable(
            name = "recommendation_keywords",
            joinColumns = @JoinColumn(name = "recommendation_result_id")
    )
    @Column(name = "keyword", nullable = false)
    private List<String> keywords;

    @Builder
    public RecommendationResult(Guest guest, RecommendationSession recommendationSession, List<String> keywords) {
        this.guest = guest;
        this.recommendationSession = recommendationSession;
        this.keywords = keywords;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
