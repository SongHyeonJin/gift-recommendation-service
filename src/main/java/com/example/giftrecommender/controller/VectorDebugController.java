package com.example.giftrecommender.controller;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.vector.ProductVectorService;
import com.example.giftrecommender.vector.VectorProductSearch;
import com.example.giftrecommender.vector.dto.VectorDebugResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/vector")
@RequiredArgsConstructor
public class VectorDebugController {

    private final ObjectProvider<ProductVectorService> productVectorServiceProvider;
    private final CrawlingProductRepository crawlingProductRepository;

    private static final double DEFAULT_THRESHOLD = 0.65;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    @GetMapping("/debug")
    public VectorDebugResponse debugVectorSearch(
            @RequestParam(name = "q") String query,
            @RequestParam(name = "minPrice", required = false) Integer minPrice,
            @RequestParam(name = "maxPrice", required = false) Integer maxPrice,
            @RequestParam(name = "gender", required = false) Gender gender,
            @RequestParam(name = "age", required = false) Age age,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "threshold", required = false) Double threshold
    ){
        ProductVectorService vectorService = productVectorServiceProvider.getIfAvailable();
        if (vectorService == null) {
            throw new IllegalStateException("Vector feature is disabled (ProductVectorService bean missing)");
        }

        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
//        double effectiveThreshold = (threshold != null) ? threshold : DEFAULT_THRESHOLD;
        Double effectiveThreshold = threshold;
        List<VectorProductSearch.ScoredId> hits;
        try {
            hits = vectorService.searchWithScores(
                    query,
                    minPrice,
                    maxPrice,
                    age != null ? age.name() : null,
                    gender != null ? gender.name() : null,
                    safeLimit,
                    effectiveThreshold
            );
        } catch (Exception e) {
            log.warn("[VECTOR][DEBUG][ERROR] q='{}', cause={}", query, e.toString());
            return new VectorDebugResponse(query, effectiveThreshold, VectorDebugResponse.ScoreType.UNKNOWN, List.of());
        }

        if (hits.isEmpty()) {
            return new VectorDebugResponse(query, effectiveThreshold, VectorDebugResponse.ScoreType.UNKNOWN, List.of());
        }

        // 1) score 타입 자동 판별
        VectorDebugResponse.ScoreType scoreType = detectScoreType(hits);

        // 2) hits 순서 유지 + DB 조회
        List<Long> ids = hits.stream()
                .map(VectorProductSearch.ScoredId::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, CrawlingProduct> productMap =
                crawlingProductRepository.findAllByIdInWithKeywords(ids).stream()
                        .collect(Collectors.toMap(CrawlingProduct::getId, p -> p, (a,b) -> a));

        // 3) rawScore + similarity 변환 포함해서 응답
        List<VectorDebugResponse.VectorDebugItem> items = hits.stream()
                .map(h -> {
                    CrawlingProduct p = productMap.get(h.productId());
                    if (p == null) return null;

                    double raw = h.score();
                    double sim = toSimilarity(raw, scoreType);

                    return new VectorDebugResponse.VectorDebugItem(
                            p.getId(),
                            raw,
                            sim,
                            p.getDisplayName(),
                            p.getCategory(),
                            p.getShortDescription(),
                            p.getPrice(),
                            p.getKeywords()
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new VectorDebugResponse(query, effectiveThreshold, scoreType, items);
    }

    /**
     * score가 similarity인지 distance인지 휴리스틱 판별
     *
     * - similarity(일반적): [0, 1] 범위로 많이 나옴 (클수록 유사)
     * - distance(가능성): [0, 2] 범위로 나올 수 있음 (작을수록 유사)
     * - 음수 존재: similarity(-1~1) 가능성
     */
    private VectorDebugResponse.ScoreType detectScoreType(List<VectorProductSearch.ScoredId> hits) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (var h : hits) {
            double s = h.score();
            min = Math.min(min, s);
            max = Math.max(max, s);
        }

        // 음수가 있다면 distance라기보단 similarity(-1~1)일 가능성이 큼
        if (min < 0.0) {
            return VectorDebugResponse.ScoreType.SIMILARITY;
        }

        // 0~1 사이면 similarity로 보는 게 합리적
        if (max <= 1.0) {
            return VectorDebugResponse.ScoreType.SIMILARITY;
        }

        // 1 초과 값이 나오면 distance일 가능성을 크게 봄 (특히 2 이하)
        if (max <= 2.0) {
            return VectorDebugResponse.ScoreType.DISTANCE;
        }

        // 그 외는 애매
        return VectorDebugResponse.ScoreType.UNKNOWN;
    }

    /**
     * scoreType에 따라 similarity로 환산
     * - DISTANCE면 similarity = 1 - distance (너희 기존 로직과 동일)
     * - SIMILARITY면 raw 그대로 사용
     * - UNKNOWN이면 raw를 그대로 similarity에 넣어두되, 클라이언트가 참고하도록 scoreType도 함께 내려줌
     */
    private double toSimilarity(double rawScore, VectorDebugResponse.ScoreType scoreType) {
        return switch (scoreType) {
            case DISTANCE -> 1.0 - rawScore;
            case SIMILARITY, UNKNOWN -> rawScore;
        };
    }
}
