package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.vector.BackfillIdView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CrawlingProduct p set p.age = :age where p.id in :ids")
    int bulkUpdateAge(@Param("ids") List<Long> ids, @Param("age") Age age);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CrawlingProduct p set p.gender = :gender where p.id in :ids")
    int bulkUpdateGender(@Param("ids") List<Long> ids, @Param("gender") Gender gender);

    @Modifying(clearAutomatically = true)
    @Query("update CrawlingProduct p set p.embeddingReady = true where p.id = :id")
    int markEmbeddingReady(@Param("id") Long id);

    @Query("select p.embeddingReady from CrawlingProduct p where p.id = :id")
    Boolean isEmbeddingReady(@Param("id") Long id);

    // keywords 존재 & 비어있지 않고 vectorPointId도 존재하는 것만
    @Query("""
      select p.id as id, p.vectorPointId as vectorPointId
      from CrawlingProduct p
      where p.vectorPointId is not null
        and p.vectorPointId <> 0
        and p.keywords is not empty
    """)
    List<BackfillIdView> findAllIdsForQdrantKeywordBackfill();

    // 단건 키워드만 조회
    @Query("""
        select kw
        from CrawlingProduct p
        join p.keywords kw
        where p.id = :id
    """)
    List<String> findKeywordsById(@Param("id") Long id);

    List<CrawlingProduct> findTop500ByPriceBetweenOrderByIdDesc(int minPrice, int maxPrice);

    List<CrawlingProduct> findTop200ByOrderByIdDesc();

    @Query("""
       select p from CrawlingProduct p
       where (p.price between :minPrice and :maxPrice)
         and ( lower(p.displayName) like lower(concat('%', :kw, '%'))
            or lower(coalesce(p.category, '')) like lower(concat('%', :kw, '%')) )
       order by coalesce(p.score, 0) desc, p.id desc
       """)
    List<CrawlingProduct> findTopByNameOrCategoryLikeWithinPrice(
            @Param("kw") String keyword,
            @Param("minPrice") int minPrice,
            @Param("maxPrice") int maxPrice,
            Pageable pageable
    );

    List<CrawlingProduct> findByIdIn(Collection<Long> ids);

}
