package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCoupangProductId(Long coupangProductId);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.keywordGroups kg WHERE kg.mainKeyword LIKE %:keyword%")
    List<Product> findByKeyword(@Param("keyword") String keyword);

}
