package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.entity.RecommendationSession;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.domain.repository.RecommendationSessionRepository;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.CrawlingProductRecommendationResponseDto;
import com.example.giftrecommender.dto.response.product.CrawlingProductResponseDto;
import com.example.giftrecommender.util.RecommendationUtil;
import com.example.giftrecommender.vector.VectorProductSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class RecommendationVectorService {

    /* =========================================================
     *  Constants (Recommendation)
     * ========================================================= */

    /** 추천 응답 상한(기존 추천 API는 8개 고정) */
    private static final int TARGET_RESULT_SIZE = 8;

    /** 키워드별 1차 확보 목표(추천에서만 사용) */
    private static final int PER_KEYWORD_PRIMARY = 2;

    /** 키워드 버퍼(추천 재분배용) */
    private static final int PER_KEYWORD_BUFFER = 4;

    /** 후보 풀 상한(메모리/정렬 과부하 방지) */
    private static final int CANDIDATE_POOL_LIMIT = 1200;

    /* =========================================================
     *  Constants (Vector Search)
     * ========================================================= */

    /** 기본 threshold(벡터 재확인 등에 사용) */
    private static final double VECTOR_THRESHOLD_DEFAULT = 0.78;

    /** 벡터 검색 topK 배수(리콜 확보용) */
    private static final int VECTOR_TOPK_MULTIPLIER = 4;

    /** 제목 유사 중복 제거 컷오프(자카드) */
    private static final double TITLE_SIMILARITY_CUTOFF = 0.85;

    /** 사용자 입력 키워드 수 제한 */
    private static final int MAX_KEYWORDS = 10;

    /** 토큰 코사인 근사 매칭 임계값(약한 보조 판정) */
    private static final double COSINE_SIM_THRESHOLD = 0.35;

    /* =========================================================
     *  Dependencies
     * ========================================================= */

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final VectorProductSearch vectorProductSearch;
    private final CrawlingProductRepository crawlingProductRepository;
    private final CrawlingProductImportService crawlingProductImportService;

    /** (상품, 내부 점수) 전달용 경량 레코드 */
    private record Scored(CrawlingProduct p, double s) {}

    /* =========================================================
     *  Public API - Recommendation
     * ========================================================= */

    /**
     * 벡터+DB+외부를 결합한 "추천" 진입점
     * - 키워드별 2개 우선 확보 → 재분배 → 부족 시 전역 보충 → DTO 변환(8개)
     */
    @Transactional
    public CrawlingProductRecommendationResponseDto recommendByVector(
            UUID guestId, UUID sessionId, RecommendationRequestDto request
    ) {
        // 0) 세션/게스트 검증
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        // 1) 키워드 정규화(중복 제거 + 최대 10개)
        List<String> keywords = normalizeKeywords(request.keywords());
        if (keywords.isEmpty()) {
            log.info("[RECO_VECTOR] empty keywords guestId={}, sessionId={}", guestId, sessionId);
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        int minPrice = request.minPrice();
        int maxPrice = request.maxPrice();
        Gender reqGender = request.gender();

        // 유아/아기 도메인 가드(요청 컨텍스트 기반 허용/차단)
        boolean babyContext = isBabyContext(request);

        // 기대 개수(키워드 수가 적을 때는 그에 맞춰 축소)
        int expectedCount = Math.min(
                TARGET_RESULT_SIZE,
                Math.max(PER_KEYWORD_PRIMARY, keywords.size() * PER_KEYWORD_PRIMARY)
        );

        log.info("[RECO_VECTOR][REQ] guestId={}, sessionId={}, kws={}, expected={}, price=[{},{}], gender={}, babyContext={}",
                guestId, sessionId, keywords, expectedCount, Math.max(minPrice, 0),
                (maxPrice <= 0 ? "MAX" : maxPrice), reqGender, babyContext);

        // 2) 후보 수집(DB → 벡터(DB존재만) → 외부)
        List<CrawlingProduct> candidates = collectCandidates(
                keywords, minPrice, maxPrice, expectedCount, request, babyContext
        );

        log.debug("[RECO_VECTOR] candidates={} (before rebalance)", candidates.size());

        // 3) 재분배(키워드별 2개 보장) + 성별/가격/도메인 가드 반영
        List<CrawlingProduct> balanced = applyFinalFiltersWithRebalance(
                candidates, minPrice, maxPrice, keywords, reqGender,
                expectedCount, PER_KEYWORD_PRIMARY, PER_KEYWORD_BUFFER, babyContext
        );

        log.debug("[RECO_VECTOR] balanced={} (after rebalance)", balanced.size());

        // 4) 8개 미만이면 전역 보충(DB 근사 → 외부)
        if (balanced.size() < TARGET_RESULT_SIZE) {
            int before = balanced.size();
            balanced = globalTopUp(balanced, keywords, minPrice, maxPrice, reqGender, request, babyContext);
            log.info("[RECO_VECTOR][TOPUP] before={}, after={}", before, balanced.size());
        }

        // 5) DTO 변환(상한 8개)
        List<CrawlingProductResponseDto> items = balanced.stream()
                .limit(TARGET_RESULT_SIZE)
                .map(CrawlingProductResponseDto::from)
                .toList();

        log.info("[RECO_VECTOR][RES] out={}", items.size());
        return new CrawlingProductRecommendationResponseDto(items);
    }

    /* =========================================================
     *  Public API - Vector Search (프론트 2차 필터링용)
     * ========================================================= */

    /**
     * "검색처럼" 쓰기 위한 벡터 검색 API
     *
     * - 프론트가 slotKeyword(예: "주얼리", "향수") 단위로 호출
     * - 벡터 검색 결과를 충분히 크게 가져온 뒤
     *   (1) dominant 대분류 정합성 필터 + (2) slotKeyword 매칭 필터(2차 필터)
     * - 그 다음 가격/베이비가드/중복 제거로 최종 limit개 반환
     *
     * ✅ 컨트롤러 순서 그대로:
     * (guestId, sessionId, keyword, minPrice, maxPrice, limit, threshold)
     */
    @Transactional(readOnly = true)
    public CrawlingProductRecommendationResponseDto searchByVector(
            String keyword,
            Integer minPrice,
            Integer maxPrice,
            int limit,
            Double threshold
    ) {
        String slot = Optional.ofNullable(keyword).orElse("").trim();
        if (slot.isBlank()) {
            log.debug("[SEARCH_VECTOR] empty keyword");
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));

        int min = Math.max(Optional.ofNullable(minPrice).orElse(0), 0);
        int max = maxOrMaxInt(Optional.ofNullable(maxPrice).orElse(0));

        // 검색 API는 로그인/세션 맥락 제거 → 성별/아기 컨텍스트도 기본 미적용
        Gender reqGender = null;
        boolean babyContext = false;

        log.info("[SEARCH_VECTOR][REQ] slot='{}', limit={}, price=[{},{}], threshold={}",
                slot, safeLimit, min, (max == Integer.MAX_VALUE ? "MAX" : max), threshold);

        String query = buildVectorQueryForSearch(slot);
        if (query.isBlank()) {
            log.warn("[SEARCH_VECTOR] blank query slot='{}'", slot);
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        int topK = Math.max(safeLimit * 5, 120);

        // ✅ threshold는 null 그대로 허용 (Qdrant 쪽이 null-safe여야 함)
        List<VectorProductSearch.ScoredId> hits;
        try {
            hits = vectorProductSearch.searchWithScores(
                    query,
                    min,
                    max,
                    null,
                    null,
                    topK,
                    threshold // null 가능
            );
        } catch (Exception e) {
            log.warn("[SEARCH_VECTOR][FAIL] slot='{}', err={}", slot, e.toString());
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        if (hits == null || hits.isEmpty()) {
            log.info("[SEARCH_VECTOR][RES] slot='{}' hits=0", slot);
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        // productId가 primitive(long)일 수 있으므로 Long.valueOf로 맵핑
        List<Long> ids = hits.stream()
                .map(h -> Long.valueOf(h.productId()))
                .filter(id -> id > 0)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            log.info("[SEARCH_VECTOR][RES] slot='{}' ids=0", slot);
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        Map<Long, Double> scoreMap = hits.stream()
                .map(h -> Map.entry(Long.valueOf(h.productId()), h.score()))
                .filter(e -> e.getKey() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a
                ));

        List<CrawlingProduct> loaded = new ArrayList<>();
        crawlingProductRepository.findAllById(ids).forEach(loaded::add);

        Map<Long, CrawlingProduct> productMap = loaded.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(CrawlingProduct::getId, p -> p, (a, b) -> a));

        List<Scored> scored = ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(p -> new Scored(p, scoreMap.getOrDefault(p.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(Scored::s).reversed())
                .toList();

        if (scored.isEmpty()) {
            log.info("[SEARCH_VECTOR][RES] slot='{}' scored=0 (no DB matched)", slot);
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        SecondStageResult second = secondStageFilterWithReport(scored, slot);

        TakeReport take = takeFilteredUniqueWithReport(
                second.filtered,
                safeLimit,
                min, max,
                reqGender,
                babyContext
        );

        List<CrawlingProductResponseDto> items = take.result.stream()
                .map(CrawlingProductResponseDto::from)
                .toList();

        log.info("[SEARCH_VECTOR][RES] slot='{}' out={}", slot, items.size());
        return new CrawlingProductRecommendationResponseDto(items);
    }

    /* =========================================================
     *  Search helper reports
     * ========================================================= */

    private record SecondStageResult(
            List<Scored> filtered,
            int majorFilteredSize,
            int slotMatchedSize,
            String dominantMajor,
            double dominantRatio
    ) {}

    private record DominantReport(List<Scored> filtered, String dominantMajor, double ratio) {}

    private record TakeReport(
            List<CrawlingProduct> result,
            int outSize,
            int dropPrice,
            int dropGender,
            int dropBaby,
            int dropDup
    ) {}

    private SecondStageResult secondStageFilterWithReport(List<Scored> scored, String keywordSlot) {
        if (scored == null || scored.isEmpty()) {
            return new SecondStageResult(List.of(), 0, 0, "", 0.0);
        }

        DominantReport dom = dominantMajorReport(scored);
        List<Scored> majorFiltered = dom.filtered;

        List<Scored> slotMatched = majorFiltered.stream()
                .filter(sc -> sc.p() != null && keywordMatches(sc.p(), keywordSlot))
                .toList();

        return new SecondStageResult(
                slotMatched,
                majorFiltered.size(),
                slotMatched.size(),
                dom.dominantMajor,
                dom.ratio
        );
    }

    private DominantReport dominantMajorReport(List<Scored> scored) {
        if (scored.size() < 12) {
            return new DominantReport(scored, "", 0.0);
        }

        int window = Math.min(40, scored.size());
        List<Scored> top = scored.subList(0, window);

        Map<String, Long> freq = top.stream()
                .map(sc -> majorCategory(Optional.ofNullable(sc.p()).map(CrawlingProduct::getCategory).orElse(null)))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        if (freq.isEmpty()) return new DominantReport(scored, "", 0.0);

        Map.Entry<String, Long> dominant = freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (dominant == null) return new DominantReport(scored, "", 0.0);

        double ratio = dominant.getValue() / (double) window;

        // ratio < 0.5면 컷 안함(과필터 방지)
        if (ratio < 0.5) {
            return new DominantReport(scored, dominant.getKey(), ratio);
        }

        String dom = dominant.getKey();
        List<Scored> filtered = scored.stream()
                .filter(sc -> dom.equals(majorCategory(Optional.ofNullable(sc.p()).map(CrawlingProduct::getCategory).orElse(null))))
                .toList();

        return new DominantReport(filtered, dom, ratio);
    }

    private TakeReport takeFilteredUniqueWithReport(
            List<Scored> incoming,
            int limit,
            int minPrice, int maxPrice,
            Gender reqGender,
            boolean babyContext
    ) {
        List<CrawlingProduct> out = new ArrayList<>(limit);

        Set<Long> pickedIds = new HashSet<>();
        Set<String> seenTitleKeys = new HashSet<>();

        int dropPrice = 0, dropGender = 0, dropBaby = 0, dropDup = 0;

        for (Scored sc : incoming) {
            if (out.size() >= limit) break;

            CrawlingProduct p = sc.p();
            if (p == null) continue;

            // 가격
            if (!withinPrice(p, minPrice, maxPrice)) { dropPrice++; continue; }

            // 성별 차단(현재 search는 reqGender=null 이라 사실상 패스)
            if (RecommendationUtil.blockedByGender(reqGender, p)) { dropGender++; continue; }

            // 베이비 도메인 차단
            if (isBabyDomain(p) && !babyContext) { dropBaby++; continue; }

            // id/url 중복
            Long id = p.getId();
            if (id != null && pickedIds.contains(id)) { dropDup++; continue; }
            if (alreadyContains(out, p)) { dropDup++; continue; }

            // 제목 유사 중복(자카드)
            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String titleKey = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");

            boolean dup = false;
            for (String existing : seenTitleKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= TITLE_SIMILARITY_CUTOFF) { dup = true; break; }
            }
            if (dup) { dropDup++; continue; }

            seenTitleKeys.add(titleKey);
            if (id != null) pickedIds.add(id);
            out.add(p);
        }

        return new TakeReport(out, out.size(), dropPrice, dropGender, dropBaby, dropDup);
    }

    /* =========================================================
     *  Candidate Collect (추천용)
     * ========================================================= */

    @Transactional(readOnly = true)
    protected List<CrawlingProduct> collectCandidates(
            List<String> keywords,
            int minPrice, int maxPrice,
            int targetSize,
            RecommendationRequestDto request,
            boolean babyContext
    ) {
        int cap = Math.max(1, targetSize);
        List<CrawlingProduct> acc = new ArrayList<>(cap * 3);

        Set<String> seenKeys = new HashSet<>();
        Set<Long> pickedIds = new HashSet<>();

        Pageable top20 = PageRequest.of(0, 20);

        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            if (acc.size() >= cap * 3) break;

            int needForKw = PER_KEYWORD_PRIMARY;

            List<CrawlingProduct> dbStrict = loadFromDbByNameOrCategory(kw, minPrice, maxPrice, top20);
            dbStrict.removeIf(p -> isBabyDomain(p) && !babyContext);

            List<CrawlingProduct> dbStrictMutable = new ArrayList<>(dbStrict);
            dbStrictMutable.sort(Comparator
                    .comparing((CrawlingProduct p) -> Optional.ofNullable(p.getScore()).orElse(0))
                    .reversed()
            );

            int addedStrict = fillWithRulesLimitedForKeyword(
                    acc, cap * 3, needForKw,
                    dbStrictMutable.stream()
                            .map(p -> new Scored(p, Optional.ofNullable(p.getScore()).orElse(0)))
                            .toList(),
                    kw,
                    TITLE_SIMILARITY_CUTOFF, seenKeys, pickedIds
            );
            needForKw -= addedStrict;

            if (needForKw > 0) {
                List<Scored> scoredSim = vectorSimilarFromDB(
                        kw, minPrice, maxPrice, needForKw, pickedIds, request, keywords
                ).stream()
                        .filter(sc -> !(isBabyDomain(sc.p()) && !babyContext))
                        .toList();

                int addedSim = fillWithRulesLimitedForKeyword(
                        acc, cap * 3, needForKw, scoredSim,
                        kw,
                        TITLE_SIMILARITY_CUTOFF, seenKeys, pickedIds
                );
                needForKw -= addedSim;

                log.debug("[RECO_VECTOR][KW] kw='{}' needAfterVector={}", kw, needForKw);
            }

            if (needForKw > 0) {
                List<CrawlingProduct> fetched = loadFromNaverByKeyword(kw, needForKw, minPrice, maxPrice, request);
                if (!fetched.isEmpty()) {
                    fetched.removeIf(p -> isBabyDomain(p) && !babyContext);
                    List<Scored> scoredFetched = fetched.stream().map(p -> new Scored(p, 1.0)).toList();
                    fillWithRulesLimitedForKeyword(
                            acc, cap * 3, needForKw, scoredFetched,
                            kw,
                            TITLE_SIMILARITY_CUTOFF, seenKeys, pickedIds
                    );
                    log.info("[RECO_VECTOR][NAVER] kw='{}' fetched={}", kw, fetched.size());
                }
            }
        }

        if (acc.size() < cap) {
            List<CrawlingProduct> pool;
            try {
                pool = crawlingProductRepository.findTop500ByPriceBetweenOrderByIdDesc(
                        Math.max(minPrice, 0), maxOrMaxInt(maxPrice)
                );
            } catch (Exception e) {
                pool = crawlingProductRepository.findAll();
            }

            if (pool.size() > CANDIDATE_POOL_LIMIT) pool = pool.subList(0, CANDIDATE_POOL_LIMIT);
            pool.removeIf(p -> isBabyDomain(p) && !babyContext);

            List<Scored> scoredAll = pool.stream()
                    .filter(p -> withinPrice(p, minPrice, maxPrice))
                    .map(p -> new Scored(p, 0.0))
                    .toList();

            int before = acc.size();
            fillWithRulesAnyKeyword(acc, cap * 3, scoredAll, TITLE_SIMILARITY_CUTOFF, seenKeys, pickedIds, keywords);
            log.info("[RECO_VECTOR][POOL] before={}, after={}", before, acc.size());
        }

        return acc;
    }

    /* =========================================================
     *  Rebalance & TopUp (추천용)
     * ========================================================= */

    private List<CrawlingProduct> applyFinalFiltersWithRebalance(
            List<CrawlingProduct> candidates,
            int minPrice, int maxPrice,
            List<String> userKeywords,
            Gender gender, int limit,
            int perKeywordPrimary, int perKeywordBuffer,
            boolean babyContext
    ) {
        Map<String, List<CrawlingProduct>> perKeyword = new LinkedHashMap<>();
        for (String kw : userKeywords) perKeyword.put(kw, new ArrayList<>());

        List<CrawlingProduct> overflow = new ArrayList<>();

        for (CrawlingProduct p : candidates) {
            if (!withinPrice(p, minPrice, maxPrice)) continue;
            if (RecommendationUtil.blockedByGender(gender, p)) continue;
            if (isBabyDomain(p) && !babyContext) continue;

            List<String> matched = findMatchedKeywords(p, userKeywords);
            if (matched.isEmpty()) continue;

            boolean stored = false;
            for (String kw : matched) {
                List<CrawlingProduct> bucket = perKeyword.get(kw);
                if (bucket == null) continue;
                if (bucket.size() < perKeywordBuffer && !alreadyContainsBucket(bucket, p)) {
                    bucket.add(p);
                    stored = true;
                    break;
                }
            }
            if (!stored) overflow.add(p);
        }

        List<CrawlingProduct> donors = new ArrayList<>();
        for (List<CrawlingProduct> bucket : perKeyword.values()) {
            if (bucket.size() > perKeywordPrimary) {
                donors.addAll(new ArrayList<>(bucket.subList(perKeywordPrimary, bucket.size())));
            }
        }

        for (List<CrawlingProduct> bucket : perKeyword.values()) {
            if (bucket.size() >= perKeywordPrimary) continue;
            Iterator<CrawlingProduct> it = donors.iterator();
            while (bucket.size() < perKeywordPrimary && it.hasNext()) {
                CrawlingProduct d = it.next();
                if (alreadyContainsBucket(bucket, d)) { it.remove(); continue; }
                bucket.add(d);
                it.remove();
            }
        }

        for (List<CrawlingProduct> bucket : perKeyword.values()) {
            if (bucket.size() >= perKeywordPrimary) continue;
            Iterator<CrawlingProduct> it = overflow.iterator();
            while (bucket.size() < perKeywordPrimary && it.hasNext()) {
                CrawlingProduct o = it.next();
                if (alreadyContainsBucket(bucket, o)) { it.remove(); continue; }
                bucket.add(o);
                it.remove();
            }
        }

        List<CrawlingProduct> finalResult = new ArrayList<>(limit);

        for (String kw : userKeywords) {
            List<CrawlingProduct> bucket = perKeyword.get(kw);
            if (bucket == null) continue;
            if (bucket.size() >= 2) {
                int take = Math.min(perKeywordPrimary, bucket.size());
                for (int i = 0; i < take && finalResult.size() < limit; i++) {
                    CrawlingProduct p = bucket.get(i);
                    if (alreadyContains(finalResult, p)) continue;
                    finalResult.add(p);
                }
            }
            if (finalResult.size() >= limit) break;
        }

        if (finalResult.size() < limit) {
            for (String kw : userKeywords) {
                List<CrawlingProduct> bucket = perKeyword.get(kw);
                if (bucket == null) continue;
                if (bucket.size() == 1) {
                    CrawlingProduct p = bucket.get(0);
                    if (!alreadyContains(finalResult, p)) {
                        finalResult.add(p);
                        if (finalResult.size() >= limit) break;
                    }
                }
            }
        }

        if (finalResult.size() < limit) {
            for (CrawlingProduct d : donors) {
                if (finalResult.size() >= limit) break;
                if (alreadyContains(finalResult, d)) continue;
                finalResult.add(d);
            }
        }
        if (finalResult.size() < limit) {
            for (CrawlingProduct o : overflow) {
                if (finalResult.size() >= limit) break;
                if (alreadyContains(finalResult, o)) continue;
                finalResult.add(o);
            }
        }

        return finalResult.size() > limit ? new ArrayList<>(finalResult.subList(0, limit)) : finalResult;
    }

    private List<CrawlingProduct> globalTopUp(
            List<CrawlingProduct> current,
            List<String> keywords,
            int minPrice, int maxPrice,
            Gender gender,
            RecommendationRequestDto request,
            boolean babyContext
    ) {
        List<CrawlingProduct> acc = new ArrayList<>(current);

        Set<Long> haveIds = acc.stream()
                .map(CrawlingProduct::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> seenKey = new HashSet<>();
        for (CrawlingProduct p : acc) {
            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");
            seenKey.add(key);
        }

        if (acc.size() < TARGET_RESULT_SIZE) {
            List<CrawlingProduct> pool;
            try {
                pool = crawlingProductRepository.findTop500ByPriceBetweenOrderByIdDesc(
                        Math.max(minPrice, 0),
                        maxOrMaxInt(maxPrice)
                );
            } catch (Exception e) {
                pool = crawlingProductRepository.findAll();
            }

            List<Scored> sims = new ArrayList<>();
            for (CrawlingProduct p : pool) {
                if (!withinPrice(p, minPrice, maxPrice)) continue;
                if (isBabyDomain(p) && !babyContext) continue;

                double maxCos = 0.0;
                for (String kw : keywords) {
                    maxCos = Math.max(maxCos, cosineKeywordSimilarity(kw, p));
                }
                if (maxCos >= COSINE_SIM_THRESHOLD) sims.add(new Scored(p, maxCos));
            }
            sims.sort(Comparator.comparingDouble(Scored::s).reversed());

            addUntil(acc, sims, TARGET_RESULT_SIZE, seenKey, haveIds, gender);
        }

        if (acc.size() < TARGET_RESULT_SIZE) {
            int remain = TARGET_RESULT_SIZE - acc.size();
            for (String kw : keywords) {
                if (remain <= 0) break;
                List<CrawlingProduct> fetched = loadFromNaverByKeyword(
                        kw, Math.max(2, remain), minPrice, maxPrice, request
                );
                if (!fetched.isEmpty()) {
                    fetched.removeIf(p -> isBabyDomain(p) && !babyContext);
                    List<Scored> scored = fetched.stream().map(p -> new Scored(p, 1.0)).toList();
                    addUntil(acc, scored, TARGET_RESULT_SIZE, seenKey, haveIds, gender);
                    remain = TARGET_RESULT_SIZE - acc.size();
                }
            }
        }

        return acc.size() > TARGET_RESULT_SIZE
                ? new ArrayList<>(acc.subList(0, TARGET_RESULT_SIZE))
                : acc;
    }

    private void addUntil(
            List<CrawlingProduct> acc,
            List<Scored> incoming,
            int limit,
            Set<String> seenKeys,
            Set<Long> haveIds,
            Gender gender
    ) {
        for (Scored sc : incoming) {
            if (acc.size() >= limit) break;

            CrawlingProduct p = sc.p();
            if (p == null) continue;

            if (RecommendationUtil.blockedByGender(gender, p)) continue;

            Long id = p.getId();
            if (id != null && haveIds.contains(id)) continue;

            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");

            boolean dupTitle = false;
            for (String existing : seenKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= TITLE_SIMILARITY_CUTOFF) { dupTitle = true; break; }
            }
            if (dupTitle) continue;

            seenKeys.add(key);
            if (id != null) haveIds.add(id);
            acc.add(p);
        }
    }

    /* =========================================================
     *  Vector Similar (추천 후보용) + 2차 필터
     * ========================================================= */

    private List<Scored> vectorSimilarFromDB(
            String keyword,
            int minPrice, int maxPrice,
            int need,
            Set<Long> excludeIds,
            RecommendationRequestDto req,
            List<String> allKws
    ) {
        String q = buildVectorQuery(
                Optional.ofNullable(req.preference()).orElse(""),
                keyword, req, allKws
        );
        if (q.isBlank()) return List.of();

        String reqAge = Optional.ofNullable(req.age()).orElse(null);

        int topK = Math.max(need * VECTOR_TOPK_MULTIPLIER * 3, 60);

        List<VectorProductSearch.ScoredId> hits;
        try {
            hits = vectorProductSearch.searchWithScores(
                    q,
                    Math.max(minPrice, 0),
                    maxOrMaxInt(maxPrice),
                    reqAge,
                    null,
                    topK,
                    null
            );
        } catch (Exception e) {
            log.warn("[RECO_VECTOR][VEC_FAIL] kw='{}', err={}", keyword, e.toString());
            return List.of();
        }
        if (hits == null || hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream()
                .map(h -> Long.valueOf(h.productId()))
                .filter(id -> id > 0)
                .filter(id -> !excludeIds.contains(id))
                .distinct()
                .toList();
        if (ids.isEmpty()) return List.of();

        Map<Long, Double> scoreMap = hits.stream()
                .map(h -> Map.entry(Long.valueOf(h.productId()), h.score()))
                .filter(e -> e.getKey() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a
                ));

        List<CrawlingProduct> loaded = new ArrayList<>();
        crawlingProductRepository.findAllById(ids).forEach(loaded::add);

        List<Scored> scored = loaded.stream()
                .map(p -> new Scored(p, scoreMap.getOrDefault(p.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(Scored::s).reversed())
                .toList();

        SecondStageResult second = secondStageFilterWithReport(scored, keyword);

        log.debug("[RECO_VECTOR][VEC_2ND] kw='{}' before={}, majorFiltered={}, slotMatched={}, dominantMajor='{}', ratio={}",
                keyword, scored.size(), second.majorFilteredSize, second.slotMatchedSize, second.dominantMajor, second.dominantRatio);

        return second.filtered.stream()
                .sorted(Comparator.comparingDouble(Scored::s).reversed())
                .toList();
    }

    /* =========================================================
     *  DB / External Load
     * ========================================================= */

    private List<CrawlingProduct> loadFromDbByNameOrCategory(
            String keyword, int minPrice, int maxPrice, Pageable top20
    ) {
        String kw = keyword.trim();
        int min = Math.max(minPrice, 0);
        int max = maxOrMaxInt(maxPrice);

        List<CrawlingProduct> list =
                crawlingProductRepository.findTopByNameOrCategoryLikeWithinPrice(kw, min, max, top20);

        return new ArrayList<>(list.subList(0, Math.min(20, list.size())));
    }

    private List<CrawlingProduct> loadFromNaverByKeyword(
            String kw,
            int need,
            int minPrice,
            int maxPrice,
            RecommendationRequestDto request
    ) {
        if (need <= 0) return List.of();
        try {
            return crawlingProductImportService.fetchForKeyword(
                    kw,
                    minPrice,
                    maxPrice,
                    Optional.ofNullable(request.age()).orElse(""),
                    Optional.ofNullable(request.reason()).orElse(""),
                    Optional.ofNullable(request.preference()).orElse(""),
                    need
            );
        } catch (Exception e) {
            log.warn("[NAVER_FETCH_FAIL] kw='{}', need={}, err={}", kw, need, e.toString());
            return List.of();
        }
    }

    /* =========================================================
     *  Insert Helpers (추천 후보 적재용)
     * ========================================================= */

    private int fillWithRulesLimitedForKeyword(
            List<CrawlingProduct> acc,
            int cap,
            int quotaForThisKeyword,
            List<Scored> scored,
            String keywordForThisSlot,
            double titleJacCutoff,
            Set<String> seenKeys,
            Set<Long> pickedIds
    ) {
        int added = 0;

        for (Scored sc : scored) {
            if (acc.size() >= cap) break;
            if (added >= quotaForThisKeyword) break;

            CrawlingProduct p = sc.p();
            if (p == null) continue;

            Long id = p.getId();
            if (id != null && pickedIds.contains(id)) continue;

            if (!keywordMatches(p, keywordForThisSlot)) continue;

            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");

            boolean dupTitle = false;
            for (String existing : seenKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= titleJacCutoff) { dupTitle = true; break; }
            }
            if (dupTitle) continue;

            seenKeys.add(key);
            acc.add(p);
            if (id != null) pickedIds.add(id);
            added++;
        }

        return added;
    }

    private void fillWithRulesAnyKeyword(
            List<CrawlingProduct> acc,
            int cap,
            List<Scored> scored,
            double titleJacCutoff,
            Set<String> seenKeys,
            Set<Long> pickedIds,
            List<String> userKws
    ) {
        for (Scored sc : scored) {
            if (acc.size() >= cap) break;

            CrawlingProduct p = sc.p();
            if (p == null) continue;

            Long id = p.getId();
            if (id != null && pickedIds.contains(id)) continue;

            if (!matchesAnyUserKeyword(p, userKws)) continue;

            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");

            boolean dupTitle = false;
            for (String existing : seenKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= titleJacCutoff) { dupTitle = true; break; }
            }
            if (dupTitle) continue;

            seenKeys.add(key);
            acc.add(p);
            if (id != null) pickedIds.add(id);
        }
    }

    /* =========================================================
     *  Domain Guards (baby/gender/price)
     * ========================================================= */

    private boolean isBabyContext(RecommendationRequestDto req) {
        String pref = Optional.ofNullable(req.preference()).orElse("");
        String reason = Optional.ofNullable(req.reason()).orElse("");
        String age = Optional.ofNullable(req.age()).orElse("");

        return containsAnyIgnoreCase(pref, "출산", "육아", "베이비", "유아", "영유아")
                || containsAnyIgnoreCase(reason, "출산", "출산선물", "돌잔치", "백일")
                || containsAnyIgnoreCase(age, "영유아", "유아", "아기");
    }

    private boolean isBabyDomain(CrawlingProduct p) {
        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
        String category = Optional.ofNullable(p.getCategory()).orElse("");
        List<String> tags = Optional.ofNullable(p.getKeywords()).orElse(List.of());

        if (containsAnyIgnoreCase(title, "아기", "유아", "영유아", "출산", "육아")) return true;
        if (containsAnyIgnoreCase(category, "유아", "아동", "유아동", "출산", "육아")) return true;

        for (String t : tags) {
            if (containsAnyIgnoreCase(t, "아기", "유아", "영유아", "출산", "육아")) return true;
        }
        return false;
    }

    /* =========================================================
     *  Keyword Matching (slot 정합성)
     * ========================================================= */

    private boolean keywordMatches(CrawlingProduct p, String kw) {
        if (p == null || kw == null || kw.isBlank()) return false;

        String k = kw.toLowerCase(Locale.ROOT).trim();

        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
        String titleLower = Optional.ofNullable(title).orElse("").toLowerCase(Locale.ROOT);
        if (titleLower.contains(k)) return true;

        List<String> tags = Optional.ofNullable(p.getKeywords()).orElse(List.of());
        boolean inTags = tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .anyMatch(t -> t.contains(k));
        if (inTags) return true;

        String catLower = Optional.ofNullable(p.getCategory()).orElse("")
                .toLowerCase(Locale.ROOT);
        if (!catLower.isBlank() && catLower.contains(k)) return true;

        if (p.getId() != null && vectorConfirmsMatch(p.getId(), k)) return true;

        double tokenCos = cosineKeywordSimilarity(k, p);
        return tokenCos >= COSINE_SIM_THRESHOLD;
    }

    private boolean matchesAnyUserKeyword(CrawlingProduct p, List<String> userKws) {
        if (p == null || userKws == null || userKws.isEmpty()) return false;
        for (String kw : userKws) {
            if (keywordMatches(p, kw)) return true;
        }
        return false;
    }

    private List<String> findMatchedKeywords(CrawlingProduct p, List<String> userKws) {
        List<String> matched = new ArrayList<>();
        if (p == null || userKws == null || userKws.isEmpty()) return matched;
        for (String kw : userKws) {
            if (keywordMatches(p, kw)) matched.add(kw);
        }
        return matched;
    }

    private boolean vectorConfirmsMatch(Long productId, String keyword) {
        try {
            String q = "키워드:" + keyword;
            int topK = 30;

            List<VectorProductSearch.ScoredId> hits = vectorProductSearch.searchWithScores(
                    q,
                    0,
                    Integer.MAX_VALUE,
                    null,
                    null,
                    topK,
                    VECTOR_THRESHOLD_DEFAULT
            );

            if (hits == null || hits.isEmpty()) return false;

            for (VectorProductSearch.ScoredId h : hits) {
                Long hid = Long.valueOf(h.productId());
                if (Objects.equals(hid, productId) && h.score() >= VECTOR_THRESHOLD_DEFAULT) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("[VEC_CONFIRM_FAIL] kw='{}', id={}, err={}", keyword, productId, e.toString());
        }
        return false;
    }

    /* =========================================================
     *  Category helpers (dominant major)
     * ========================================================= */

    private String majorCategory(String category) {
        if (category == null || category.isBlank()) return "";
        String c = category.replace("/", ">").trim();
        int idx = c.indexOf(">");
        return (idx > 0) ? c.substring(0, idx).trim() : c.trim();
    }

    /* =========================================================
     *  Common utils
     * ========================================================= */

    private static boolean withinPrice(CrawlingProduct p, int minPrice, int maxPrice) {
        int price = Optional.ofNullable(p.getPrice()).orElse(0);
        if (minPrice > 0 && price < minPrice) return false;
        if (maxPrice > 0 && price > maxPrice) return false;
        return true;
    }

    private static int maxOrMaxInt(int maxPrice) {
        return (maxPrice > 0) ? maxPrice : Integer.MAX_VALUE;
    }

    private static List<String> normalizeKeywords(List<String> input) {
        if (input == null) return Collections.emptyList();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : input) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set.stream().limit(MAX_KEYWORDS).collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        String norm = s.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-zA-Z가-힣]", " ");
        String[] arr = norm.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String w : arr) if (!w.isBlank()) out.add(w);
        return out;
    }

    private String buildVectorQuery(
            String preference,
            String baseKw,
            RecommendationRequestDto req,
            List<String> allKws
    ) {
        String others = allKws.stream()
                .filter(k -> k != null && !k.equals(baseKw))
                .limit(4)
                .collect(Collectors.joining(", "));

        String rel = Optional.ofNullable(req.relation()).orElse("");
        String age = Optional.ofNullable(req.age()).orElse("");
        String reason = Optional.ofNullable(req.reason()).orElse("");
        String pref = Optional.ofNullable(preference).orElse("");

        return String.format(
                "핵심키워드:[%s]; 보조키워드:[%s]. 힌트: relation=%s, age=%s, reason=%s, preference=%s",
                baseKw, others, rel, age, reason, pref
        );
    }

    /** ✅ search 전용: request 없이 쿼리 구성 */
    private String buildVectorQueryForSearch(String slotKeyword) {
        String slot = Optional.ofNullable(slotKeyword).orElse("").trim();
        if (slot.isBlank()) return "";
        return "상품 검색 키워드: [" + slot + "] - 해당 키워드와 가장 관련 있는 상품을 찾아줘";
    }

    private double cosineKeywordSimilarity(String keyword, CrawlingProduct p) {
        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());

        List<String> tokens = new ArrayList<>();
        if (title != null) tokens.addAll(tokenize(title));

        List<String> tags = Optional.ofNullable(p.getKeywords()).orElse(List.of());
        for (String t : tags) {
            if (t != null) tokens.addAll(tokenize(t));
        }

        Set<String> a = new HashSet<>(tokenize(keyword));
        Set<String> b = new HashSet<>(tokens);
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int inter = 0;
        for (String x : a) if (b.contains(x)) inter++;
        return inter / Math.sqrt((double) a.size() * (double) b.size());
    }

    private boolean sameProduct(CrawlingProduct a, CrawlingProduct b) {
        if (a == null || b == null) return false;

        Long aId = a.getId();
        Long bId = b.getId();
        if (aId != null && bId != null) return aId.equals(bId);

        String aUrl = Optional.ofNullable(a.getProductUrl()).orElse("").trim();
        String bUrl = Optional.ofNullable(b.getProductUrl()).orElse("").trim();
        return !aUrl.isEmpty() && aUrl.equals(bUrl);
    }

    private boolean alreadyContains(List<CrawlingProduct> list, CrawlingProduct p) {
        if (list == null || p == null) return false;
        for (CrawlingProduct ex : list) if (sameProduct(ex, p)) return true;
        return false;
    }

    private boolean alreadyContainsBucket(List<CrawlingProduct> bucket, CrawlingProduct p) {
        if (bucket == null || p == null) return false;
        for (CrawlingProduct ex : bucket) if (sameProduct(ex, p)) return true;
        return false;
    }

    private boolean containsAnyIgnoreCase(String src, String... needles) {
        if (src == null || src.isBlank()) return false;
        String s = src.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isBlank() && s.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /* =========================================================
     *  Auth / Session Validation
     * ========================================================= */

    private Guest existsGuest(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("게스트 조회 실패: guestId={}", id);
                    return new ErrorException(ExceptionEnum.GUEST_NOT_FOUND);
                });
    }

    private RecommendationSession existsRecommendationSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("추천 세션 조회 실패: sessionId={}", id);
                    return new ErrorException(ExceptionEnum.SESSION_NOT_FOUND);
                });
    }

    private static void verifySessionOwner(RecommendationSession session, Guest guest) {
        if (!session.getGuest().getId().equals(guest.getId())) {
            log.error("세션 접근 권한 오류 | sessionId={}, guestId={}", session.getId(), guest.getId());
            throw new ErrorException(ExceptionEnum.SESSION_FORBIDDEN);
        }
    }
}
