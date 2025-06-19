package com.example.giftrecommender.domain.repository.keyword;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface KeywordGroupRepository extends JpaRepository<KeywordGroup, Long> {

    List<KeywordGroup> findByMainKeywordIn(Set<String> mainKeywords);

    @Modifying
    @Query(value = """
        INSERT INTO keyword_group (main_keyword)
        VALUES (:keyword)
        ON DUPLICATE KEY UPDATE main_keyword = main_keyword
        """,
            nativeQuery = true)
    void upsertIgnore(@Param("keyword") String keyword);

}
