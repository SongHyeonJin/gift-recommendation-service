package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.ProductRepository;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.infra.coupang.CoupangApiClient;
import com.example.giftrecommender.infra.coupang.dto.CoupangProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final CoupangApiClient coupangApiClient;
    private final ProductRepository productRepository;
    private final KeywordGroupRepository keywordGroupRepository;

    @Transactional
    public void importUntilEnough(String queryKeyword, String mainKeyword, int neededCount) {
        if (queryKeyword == null || queryKeyword.isBlank()) {
            log.warn("상품 가져오기 실패: 키워드 없음");
            return;
        }

        KeywordGroup keywordGroup = keywordGroupRepository.findByMainKeyword(mainKeyword)
                .orElseGet(() -> keywordGroupRepository.save(new KeywordGroup(mainKeyword)));

        log.info("쿠팡 API 단일 페이지 호출 (페이지=1): '{}'", queryKeyword);

        List<CoupangProductResponseDto> response = coupangApiClient.searchProducts(queryKeyword, 1);

        int saveProduct = 0;
        for (CoupangProductResponseDto dto : response) {
            long coupangId = dto.coupangProductId();

            if (productRepository.existsByCoupangProductId(coupangId)) {
                log.debug("중복 저장 스킵(DB): {}", coupangId);
                continue;
            }

            Product p = Product.builder()
                    .productUrl(UUID.randomUUID().toString())
                    .coupangProductId(coupangId)
                    .title(dto.title())
                    .price(dto.price())
                    .imageUrl(dto.imageUrl())
                    .productUrl(dto.productUrl())
                    .rank(dto.rank())
                    .isRocket(dto.isRocket())
                    .isFreeShipping(dto.isFreeShipping())
                    .keywordGroups(List.of(keywordGroup))
                    .build();
            productRepository.save(p);
            saveProduct++;

            if (saveProduct >= neededCount) break;
        }

        log.info("단일 페이지 수집 완료: {}개 (목표={})", saveProduct, neededCount);
    }
}

