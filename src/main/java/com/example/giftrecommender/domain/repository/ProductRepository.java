package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT COUNT(p) FROM Product p " +
            "JOIN p.keywordGroups kg " +
            "WHERE kg.mainKeyword = :keyword " +
            "AND p.price BETWEEN :minPrice AND :maxPrice")
    int countByKeywordAndPrice(@Param("keyword") String keyword,
                               @Param("minPrice") int minPrice,
                               @Param("maxPrice") int maxPrice);

    @Query("SELECT p FROM Product p " +
            "JOIN p.keywordGroups kg " +
            "WHERE kg.mainKeyword = :keyword " +
            "AND p.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findTopByKeywordAndPriceRange(@Param("keyword") String keyword,
                                                @Param("minPrice") int minPrice,
                                                @Param("maxPrice") int maxPrice);

    @Query("SELECT p.link FROM Product p WHERE p.link IN :links")
    Set<String> findLinksIn(@Param("links") Set<String> links);

}
