package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCoupangProductId(Long coupangProductId);

    List<Product> findByKeywordGroupsContains(KeywordGroup keywordGroup);

}
