package com.example.giftrecommender.service;

import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.ProductRepository;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.example.giftrecommender.infra.naver.NaverApiClient;
import com.example.giftrecommender.util.RecommendationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final NaverApiClient naverApiClient;
    private final ProductRepository productRepository;
    private final RedisQuotaManager redisQuotaManager;
    private final KeywordCacheService keywordCache;

    @Transactional
    public void importOneOrTwoPerKeyword(String keyword, int minPrice, int maxPrice, String age,
                                         String reason, String preference, int neededCount) {
        if (keyword == null || keyword.isBlank()) {
            log.warn("빈 키워드로 상품 수집 시도됨. 요청 무시");
            return;
        }

        KeywordGroup group = keywordCache.getOrCreate(keyword);
        if (group == null) {
            log.warn("키워드 그룹 조회 실패: {}", keyword);
            return;
        }

        Set<String> seenTitles = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();
        Set<String> seenBrands = new HashSet<>();
        List<Product> toSave = new ArrayList<>();

        for (int page = 1; page <= 10; page++) {
            redisQuotaManager.acquire();

            List<ProductResponseDto> items;
            try {
                items = naverApiClient.search(keyword, page, 100);
            } catch (Exception e) {
                log.error("Naver API 호출 실패 | keyword={}, page={}", keyword, page, e);
                break;
            }

            if (items.isEmpty()) {
                log.info("Naver API 결과 없음 | keyword={}, page={}", keyword, page);
                break;
            }

            Set<String> links = items.stream().map(ProductResponseDto::link).collect(Collectors.toSet());
            Set<String> existingLinks = productRepository.findLinksIn(links);

            for (ProductResponseDto dto : items) {
                if (existingLinks.contains(dto.link())) continue;
                if (!seenTitles.add(dto.title())) continue;

                if (!RecommendationUtil.allowBabyProduct(dto.title(), age, reason, preference)) {
                    log.debug("영유아 필터 제외: title={}", dto.title());
                    continue;
                }

                Product p = Product.from(dto, List.of(group));
                if (p.getPrice() < minPrice || p.getPrice() > maxPrice) {
                    log.debug("가격 조건 미달: title={}, price={}", dto.title(), p.getPrice());
                    continue;
                }

                String baseTitle = RecommendationUtil.extractBaseTitle(p.getTitle());
                String key = baseTitle + "::" + p.getImageUrl();
                String brand = RecommendationUtil.extractBrand(p.getBrand());

                boolean isSimilar = seenKeys.stream().anyMatch(existingKey -> {
                    String existingTitle = existingKey.split("::")[0];
                    return RecommendationUtil.jaccardSimilarityByWords(existingTitle, baseTitle) >= 0.9;
                });
                if (seenKeys.contains(key) || seenBrands.contains(brand) || isSimilar) continue;

                seenKeys.add(key);
                seenBrands.add(brand);
                toSave.add(p);

                if (toSave.size() >= neededCount) break;
            }
            if (toSave.size() >= neededCount) break;
        }

        if (!toSave.isEmpty()) {
            productRepository.saveAll(toSave);
            log.info("상품 저장 완료 | keyword=[{}], 저장 수={}", keyword, toSave.size());
        } else {
            log.warn("상품 저장 실패: 조건을 만족하는 결과 없음 | keyword=[{}], minPrice={}, maxPrice={}", keyword, minPrice, maxPrice);
        }
    }

}