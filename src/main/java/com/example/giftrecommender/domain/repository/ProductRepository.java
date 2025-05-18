package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p JOIN p.keywordGroups kg WHERE kg.mainKeyword IN :keywords AND p.price BETWEEN :min AND :max")
    List<Product> findTopByTagsAndPriceRange(
            @Param("keywords") List<String> keywords,
            @Param("min") int min,
            @Param("max") int max
    );

    @Query("SELECT p.link FROM Product p WHERE p.link IN :links")
    Set<String> findLinksIn(@Param("links") Set<String> links);

}
