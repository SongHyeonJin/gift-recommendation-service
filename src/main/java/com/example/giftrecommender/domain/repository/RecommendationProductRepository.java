package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.RecommendationProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationProductRepository extends JpaRepository<RecommendationProduct, Long> {
}
