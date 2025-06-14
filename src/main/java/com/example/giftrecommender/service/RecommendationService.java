package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import com.example.giftrecommender.dto.response.RecommendedProductResponseDto;
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
public class RecommendationService {

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final ProductRepository productRepository;
    private final RecommendationResultRepository resultRepository;
    private final RecommendationProductRepository recommendationProductRepository;
    private final ProductImportService productService;
    private final RedisQuotaManager quotaManager;

    @Transactional
    public RecommendationResponseDto recommend(UUID guestId, UUID sessionId, List<String> keywords) {
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        // 1. 키워드 분류
        String priceKeyword = keywords.stream().filter(k -> k.contains("만원")).findFirst().orElse("전체");
        String receiverKeyword = keywords.stream()
                .filter(k -> List.of("친구","남자친구","여자친구","엄마","아빠","남자 동료","여자 동료").contains(k))
                .findFirst().orElse("");
        String reasonKeyword = keywords.stream()
                .filter(k -> List.of("생일","기념일","감사","위로","응원","일상 선물").contains(k))
                .findFirst().orElse("");
        List<String> tagKeywords = keywords.stream()
                .filter(k -> !k.equals(priceKeyword) && !k.equals(receiverKeyword) && !k.equals(reasonKeyword))
                .collect(Collectors.toList());

        log.info("대상: {}, 가격: {}, 이유: {}, 태그: {}", receiverKeyword, priceKeyword, reasonKeyword, tagKeywords);

        // 2. 가격 필터 설정
        int minPrice = 0, maxPrice = Integer.MAX_VALUE;
        switch (priceKeyword) {
            case "1만원 이하" -> maxPrice = 10_000;
            case "1~3만원" -> { minPrice = 10_000; maxPrice = 30_000; }
            case "3~5만원" -> { minPrice = 30_000; maxPrice = 50_000; }
            case "5~10만원" -> { minPrice = 50_000; maxPrice = 100_000; }
            case "10~30만원" -> { minPrice = 100_000; maxPrice = 300_000; }
            case "30~50만원" -> { minPrice = 300_000; maxPrice = 500_000; }
        }

        // 3. 키워드 우선순위 조합 생성
        List<List<String>> combos = RecommendationUtil.generatePriorityCombos(tagKeywords, receiverKeyword, reasonKeyword);

        // 4. DB 조회 시도
        List<Product> finalProducts = findBestMatched(combos, minPrice, maxPrice);

        // 5. 조합 키워드 누락 또는 결과 부족 시 외부 API 보강
        boolean keywordMismatch = !finalProducts.isEmpty() && !containsAllComboKeywords(finalProducts, combos.get(0));
        if (finalProducts.size() < 10 || keywordMismatch) {
            if (!quotaManager.canCall()) {
                throw new ErrorException(ExceptionEnum.QUOTA_EXCEEDED);
            }
            productService.importUntilEnough(tagKeywords, priceKeyword, receiverKeyword, reasonKeyword, 10);
            finalProducts = findBestMatched(combos, minPrice, maxPrice);
        }

        log.info("추천 상품 {}개", finalProducts.size());
        finalProducts.forEach(p ->
                log.info("{} | {}원 | 태그={}", p.getTitle(), p.getPrice(),
                        p.getKeywordGroups().stream().map(KeywordGroup::getMainKeyword).toList()));

        if (finalProducts.isEmpty()) {
            throw new ErrorException(ExceptionEnum.RECOMMENDATION_EMPTY);
        }

        // 6. 결과 저장
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
                session.getName(),
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
                result.getRecommendationSession().getName(),
                products.stream().map(RecommendedProductResponseDto::from).toList()
        );
    }

    private List<Product> findBestMatched(List<List<String>> combos, int minPrice, int maxPrice) {
        List<Product> allResults = new ArrayList<>();
        Set<String> seenProductKeys = new HashSet<>();
        Set<String> seenBrands = new HashSet<>();

        int maxTotal = 10;
        int maxPerCombo = 2;

        for (List<String> combo : combos) {
            if (allResults.size() >= maxTotal) break;

            List<Product> candidates = productRepository.findTopByTagsAndPriceRange(combo, minPrice, maxPrice);
            Set<String> comboSet = new HashSet<>(combo);

            List<Product> selected = candidates.stream()
                    .filter(p -> {
                        Set<String> keywords = p.getKeywordGroups().stream()
                                .map(KeywordGroup::getMainKeyword)
                                .collect(Collectors.toSet());
                        long matched = comboSet.stream().filter(keywords::contains).count();
                        return matched >= Math.ceil(comboSet.size() * 0.5);
                    })
                    .filter(p -> {
                        String key = RecommendationUtil.extractBaseTitle(p.getTitle()) + "::" + p.getImageUrl();
                        String brand = p.getBrand();
                        boolean isDuplicate = seenProductKeys.contains(key) || seenBrands.contains(brand);
                        if (!isDuplicate) {
                            seenProductKeys.add(key);
                            seenBrands.add(brand);
                            return true;
                        }
                        return false;
                    })
                    .limit(maxPerCombo)
                    .toList();

            allResults.addAll(selected);
        }

        return allResults.size() >= 10 ? allResults : Collections.emptyList();
    }

    private boolean containsAllComboKeywords(List<Product> products, List<String> combo) {
        Set<String> totalKeywords = products.stream()
                .flatMap(p -> p.getKeywordGroups().stream())
                .map(KeywordGroup::getMainKeyword)
                .collect(Collectors.toSet());

        Set<String> comboSet = new HashSet<>(combo);
        Set<String> missing = comboSet.stream()
                .filter(k -> !totalKeywords.contains(k))
                .collect(Collectors.toSet());

        log.debug("총 키워드: {}", totalKeywords);
        log.debug("요청 콤보: {}", comboSet);
        log.debug("누락 키워드: {}", missing);

        return totalKeywords.containsAll(combo);
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
