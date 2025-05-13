package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import com.example.giftrecommender.dto.response.RecommendedProductResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final KeywordGroupRepository keywordGroupRepository;
    private final ProductImportService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RecommendationResponseDto recommend(UUID guestId, UUID sessionId, List<String> keywords) {
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);

        // 가격 필터 분리
        String priceFilter = keywords.stream()
                .filter(k -> k.contains("이하") || k.contains("이상"))
                .findFirst()
                .orElse("전체");

        List<String> tagKeywords = keywords.stream()
                .filter(k -> !k.contains("이하") && !k.contains("이상"))
                .toList();

        // 전체 키워드 문자열로 조합
        String combinedKeyword = String.join(" ", tagKeywords);
        String keywordJson = toJson(keywords);

        // 1. DB에서 먼저 조회
        List<Product> products = productRepository.findByKeyword(combinedKeyword);
        List<Product> allCandidates = new ArrayList<>(products);

        // 2. 충분하지 않으면 외부 API 호출 후 저장
        if (products.size() < 4) {
            log.info("{} 키워드로 DB에 부족하여 외부 API 호출", combinedKeyword);
            productService.importUntilEnough(combinedKeyword, combinedKeyword, 4);
            List<Product> newProducts = productRepository.findByKeyword(combinedKeyword);
            allCandidates.addAll(newProducts);
        } else {
            log.info("{} 키워드로 DB에 충분한 상품이 있어 API 생략", combinedKeyword);
        }

        // 중복 제거 후 가격 및 태그 필터링
        List<Product> filtered = allCandidates.stream()
                .distinct()
                .filter(p -> matchesPrice(p.getPrice(), priceFilter))
                .filter(p -> matchesTags(p.getKeywordGroups(), tagKeywords))
                .limit(4)
                .toList();

        RecommendationResult result = resultRepository.save(RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(session)
                .keywords(keywordJson)
                .build());

        List<RecommendationProduct> recProducts = filtered.stream()
                .map(p -> new RecommendationProduct(result, p))
                .toList();
        recommendationProductRepository.saveAll(recProducts);

        return new RecommendationResponseDto(
                filtered.stream().map(RecommendedProductResponseDto::from).toList()
        );
    }

    private Guest existsGuest(UUID guestId) {
        return guestRepository.findById(guestId).orElseThrow(
                () -> new ErrorException(ExceptionEnum.GUEST_NOT_FOUND)
        );
    }

    private RecommendationSession existsRecommendationSession(UUID sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow(
                () -> new ErrorException(ExceptionEnum.SESSION_NOT_FOUND)
        );
    }

    private String toJson(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 변경 실패", e);
        }
    }

    private boolean matchesPrice(int price, String priceFilter) {
        return switch (priceFilter) {
            case "5만원 이하" -> price <= 50000;
            case "5만원 이상 10만원 이하" -> price >= 50000 && price <= 100000;
            case "10만원 이상 20만원 이하" -> price >= 100000 && price <= 200000;
            case "20만원 이상" -> price >= 200000;
            default -> true;
        };
    }

    private boolean matchesTags(List<KeywordGroup> keywordGroups, List<String> tagKeywords) {
        List<String> productTags = keywordGroups.stream()
                .map(KeywordGroup::getMainKeyword)
                .toList();

        long matchCount = tagKeywords.stream()
                .filter(productTags::contains)
                .count();

        return matchCount >= Math.ceil(tagKeywords.size() / 2.0);
    }


}
