package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.RecommendationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {
}
