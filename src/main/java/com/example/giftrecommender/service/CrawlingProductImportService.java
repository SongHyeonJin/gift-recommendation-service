package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.example.giftrecommender.infra.naver.NaverApiClient;
import com.example.giftrecommender.util.RecommendationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class CrawlingProductImportService {

    private final NaverApiClient naverApiClient;
    private final CrawlingProductRepository crawlingProductRepository;

    /**
     * 저장 없이 네이버 API에서만 끌어와 "메모리 컬렉션"으로 반환.
     * - 운영 DB를 건드리지 않고 폴백 결과를 합치기 위한 용도.
     */
    @Transactional(readOnly = true)
    public List<CrawlingProduct> fetchForKeyword(String keyword,
                                                 int minPrice, int maxPrice,
                                                 String age, String reason, String preference,
                                                 int neededCount) {
        if (keyword == null || keyword.isBlank() || neededCount <= 0) return List.of();

        Set<String> seenKeys = new HashSet<>();
        List<CrawlingProduct> result = new ArrayList<>();

        // 이미 저장된 최근 200건 기준 중복 키
        Set<String> existKeys = crawlingProductRepository.findTop200ByOrderByIdDesc()
                .stream()
                .map(p -> RecommendationUtil.extractBaseTitle(
                        Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName()))
                        + "::" + Optional.ofNullable(p.getImageUrl()).orElse(""))
                .collect(Collectors.toSet());

        for (int page = 1; page <= 3 && result.size() < neededCount; page++) {
            List<ProductResponseDto> items;
            try {
                items = naverApiClient.search(keyword, page, 80);
            } catch (Exception e) {
                log.warn("Naver API 실패 keyword={}, page={}", keyword, page, e);
                break;
            }
            if (items == null || items.isEmpty()) break;

            for (ProductResponseDto dto : items) {
                int price = Optional.ofNullable(dto.lprice()).orElse(0);
                if ((minPrice > 0 && price < minPrice) || (maxPrice > 0 && price > maxPrice)) continue;
                if (!RecommendationUtil.allowBabyProduct(dto.title(), age, reason, preference)) continue;

                String baseTitle = RecommendationUtil.extractBaseTitle(dto.title());
                String key = baseTitle + "::" + Optional.ofNullable(dto.image()).orElse("");
                if (existKeys.contains(key)) continue;

                boolean similar = seenKeys.stream().anyMatch(ex -> {
                    String exTitle = ex.split("::", 2)[0];
                    return RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle) >= 0.90;
                });
                if (seenKeys.contains(key) || similar) continue;

                CrawlingProduct p = CrawlingProduct.builder()
                        .originalName(dto.title())
                        .displayName(baseTitle)
                        .price(price)
                        .imageUrl(dto.image())
                        .productUrl(dto.link())
                        .category(null)
                        .keywords(List.of(keyword))
                        .score(0)
                        .sellerName(dto.mallName())
                        .reviewCount(0)
                        .rating(null)
                        .platform(dto.mallName())
                        .gender(Gender.ANY)
                        .age(Age.NONE)
                        .isAdvertised(Boolean.FALSE)
                        .build();

                seenKeys.add(key);
                result.add(p);
                if (result.size() >= neededCount) break;
            }
        }

        log.info("CrawlingProduct fetch(비저장) 완료 | keyword={}, fetched={}", keyword, result.size());
        return result;
    }
}
