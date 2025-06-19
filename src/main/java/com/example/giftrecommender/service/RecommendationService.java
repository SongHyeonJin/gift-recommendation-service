package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import com.example.giftrecommender.dto.response.RecommendedProductResponseDto;
import com.example.giftrecommender.util.RecommendationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final ProductRepository productRepository;
    private final RecommendationResultRepository resultRepository;
    private final RecommendationProductRepository recommendationProductRepository;
    private final ProductImportService productService;

    @Transactional
    public RecommendationResponseDto recommend(UUID guestId, UUID sessionId, RecommendationRequestDto requestDto) {
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        int minPrice = requestDto.minPrice();
        int maxPrice = requestDto.maxPrice();
        String age = requestDto.age();
        String reason = requestDto.reason();
        String preference = requestDto.preference();
        List<String> keywords = requestDto.keywords();

        // 1. DB 조회 (키워드마다 최대 2개씩 추천)
        int expectedCount = Math.min(8, keywords.size() * 2);
        List<Product> finalProducts = findTopTwoPerKeyword(keywords, minPrice, maxPrice, age, reason, preference);

        // 2. 현재 상품 수가 부족하면 -> 부족한 키워드별로 외부 수집 시도
        if (finalProducts.size() < expectedCount) {
            for (String keyword : keywords) {
                int dbCount = productRepository.countByKeywordAndPrice(keyword, minPrice, maxPrice);
                if (dbCount >= 2) continue;

                long count = finalProducts.stream()
                        .filter(p -> p.getKeywordGroups().stream()
                                .anyMatch(g -> g.getMainKeyword().equals(keyword)))
                        .count();

                if (count < 2) {
                    productService.importOneOrTwoPerKeyword(keyword, minPrice, maxPrice, age, reason, preference, 2 - (int) count);
                }
            }

            finalProducts = findTopTwoPerKeyword(keywords, minPrice, maxPrice, age, reason, preference);
        }

        // 3. 그래도 부족하면 fallback 키워드로 보완
        if (finalProducts.size() < expectedCount) {
            List<String> fallbackKeywords = List.of("감성", "실용적인", "가성비", "인기");
            List<String> totalKeywords = new ArrayList<>(keywords);

            for (String fallback : fallbackKeywords) {
                totalKeywords.add(fallback);
                productService.importOneOrTwoPerKeyword(fallback, minPrice, maxPrice, age, reason, preference, 2);

                finalProducts = findTopTwoPerKeyword(totalKeywords, minPrice, maxPrice, age, reason, preference);
                if (finalProducts.size() >= expectedCount) break;
            }
        }

        if (finalProducts.size() > 8) {
            finalProducts = finalProducts.subList(0, 8);
        }

        if (finalProducts.isEmpty()) {
            throw new ErrorException(ExceptionEnum.RECOMMENDATION_EMPTY);
        }

        RecommendationResult result = resultRepository.save(RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(session)
                .keywords(keywords)
                .build());

        List<RecommendationProduct> recs = finalProducts.stream()
                .map(p -> RecommendationProduct.builder()
                        .recommendationResult(result)
                        .product(p)
                        .build())
                .toList();
        recommendationProductRepository.saveAll(recs);

        return new RecommendationResponseDto(
                finalProducts.stream().map(RecommendedProductResponseDto::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public RecommendationResponseDto getRecommendationResult(UUID guestId, UUID sessionId) {
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        RecommendationResult result = resultRepository.findByRecommendationSessionId(sessionId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.RESULT_NOT_FOUND));

        List<Product> products = recommendationProductRepository.findProductsByResultId(result.getId());

        return new RecommendationResponseDto(
                products.stream().map(RecommendedProductResponseDto::from).toList()
        );
    }

    private List<Product> findTopTwoPerKeyword(List<String> keywords, int minPrice, int maxPrice,
                                               String age, String reason, String preference) {
        List<Product> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Set<String> seenBrands = new HashSet<>();

        for (String keyword : keywords) {
            List<Product> candidates = productRepository.findTopByKeywordAndPriceRange(keyword, minPrice, maxPrice);

            List<Product> selected = candidates.stream()
                    .filter(p -> {
                        String baseTitle = RecommendationUtil.extractBaseTitle(p.getTitle());
                        String key = baseTitle + "::" + p.getImageUrl();
                        String brand = RecommendationUtil.extractBrand(p.getBrand());
                        boolean isSimilar = seenKeys.stream().anyMatch(existingKey -> {
                            String existingTitle = existingKey.split("::")[0];
                            return RecommendationUtil.jaccardSimilarityByWords(existingTitle, baseTitle) >= 0.9;
                        });
                        if (seenKeys.contains(key) || seenBrands.contains(brand) || isSimilar) return false;

                        if (!RecommendationUtil.allowBabyProduct(p.getTitle(), age, reason, preference)) {
                            return false;
                        }

                        seenKeys.add(key);
                        seenBrands.add(brand);
                        return true;
                    })
                    .limit(2)
                    .toList();

            result.addAll(selected);
        }
        return result;
    }

    private Guest existsGuest(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.GUEST_NOT_FOUND));
    }

    private RecommendationSession existsRecommendationSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.SESSION_NOT_FOUND));
    }

    private static void verifySessionOwner(RecommendationSession session, Guest guest) {
        if (!session.getGuest().getId().equals(guest.getId())) {
            throw new ErrorException(ExceptionEnum.SESSION_FORBIDDEN);
        }
    }
}
