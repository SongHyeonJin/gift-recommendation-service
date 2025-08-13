package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CrawlingProductRepository extends JpaRepository<CrawlingProduct, Long> {

    @Query("""
        SELECT p FROM CrawlingProduct p
        WHERE (:keyword IS NULL OR 
              p.originalName LIKE CONCAT('%', :keyword, '%') OR
              p.displayName  LIKE CONCAT('%', :keyword, '%') OR
              p.sellerName   LIKE CONCAT('%', :keyword, '%') OR
              p.category     LIKE CONCAT('%', :keyword, '%') OR
              p.platform     LIKE CONCAT('%', :keyword, '%'))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:category IS NULL OR p.category = :category)
          AND (:platform IS NULL OR p.platform = :platform)
          AND (:sellerName IS NULL OR p.sellerName = :sellerName)
          AND (:gender IS NULL OR p.gender = :gender)
          AND (:age IS NULL OR p.age = :age)
          AND (:isConfirmed IS NULL OR p.isConfirmed = :isConfirmed)
        """)
    Page<CrawlingProduct> search(
            @Param("keyword") String keyword,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("category") String category,
            @Param("platform") String platform,
            @Param("sellerName") String sellerName,
            @Param("gender") Gender gender,
            @Param("age") Age age,
            @Param("isConfirmed") Boolean isConfirmed,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
      UPDATE CrawlingProduct p
         SET p.isConfirmed = :isConfirmed,
             p.updatedAt = CURRENT_TIMESTAMP
       WHERE p.id IN :ids
    """)
    int bulkUpdateConfirm(@Param("ids") List<Long> ids, @Param("isConfirmed") boolean isConfirmed);

}
