package com.example.giftrecommender.domain.repository.keyword;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeywordGroupRepository extends JpaRepository<KeywordGroup, Long> {

    List<KeywordGroup> findByMainKeywordIn(List<String> mainKeywords);

    Optional<KeywordGroup> findByMainKeyword(String mainKeyword);
}
