package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.RecommendationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {

    Optional<RecommendationResult> findByRecommendationSessionId(UUID sessionId);

}
