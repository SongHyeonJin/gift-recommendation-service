package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.RecommendationProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationProductRepository extends JpaRepository<RecommendationProduct, Long> {

    @Query("SELECT rp.product FROM RecommendationProduct rp WHERE rp.recommendationResult.id = :resultId")
    List<Product> findProductsByResultId(@Param("resultId") Long resultId);

}
