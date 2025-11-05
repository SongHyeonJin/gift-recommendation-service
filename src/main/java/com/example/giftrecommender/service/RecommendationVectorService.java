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
    /** 최종으로 내려줄 개수(응답 상한) */
    private static final int TARGET_RESULT_SIZE = 8;

    /** 키워드당 우선 보장수(1차 목표) */
    private static final int PER_KEYWORD_PRIMARY = 2;

    /** 키워드 버퍼(여분까지 쌓아뒀다가 부족 버킷에 양도) */
    private static final int PER_KEYWORD_BUFFER = 4;

    /** 후보 풀 상한(과도한 메모리/정렬 방지) */
    private static final int CANDIDATE_POOL_LIMIT = 1200;

    /** 벡터 유사도 임계값(Qdrant 등 검색 필터) */
    private static final double VECTOR_THRESHOLD_DEFAULT = 0.78;

    /** 벡터 검색 topK 배수(리콜 확보용) */
    private static final int VECTOR_TOPK_MULTIPLIER = 4;

    /** 제목 유사 중복 제거 컷오프(자카드) */
    private static final double TITLE_SIMILARITY_CUTOFF = 0.85;

    /** 사용자 입력 키워드 수 제한(안전장치) */
    private static final int MAX_KEYWORDS = 10;

    /** 토큰 단위 코사인 근사 매칭 임계값(라이트한 보조 판정) */
    private static final double COSINE_SIM_THRESHOLD = 0.35;

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final VectorProductSearch vectorProductSearch;
    private final CrawlingProductRepository crawlingProductRepository;
    private final CrawlingProductImportService crawlingProductImportService;

    /** 후보 + 내부 점수 전달용 경량 DTO */
    private record Scored(CrawlingProduct p, double s) {}

    /**
     * 벡터+DB+외부를 결합한 추천 진입점
     * - 키워드별 2개 우선 확보 → 재분배 → 부족 시 전역 보충 → DTO 변환
     */
    @Transactional
    public CrawlingProductRecommendationResponseDto recommendByVector(
            UUID guestId, UUID sessionId, RecommendationRequestDto request) {

        // 세션/게스트 검증
        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        // 키워드 정규화(중복 제거 + 최대 10개)
        List<String> keywords = normalizeKeywords(request.keywords());
        if (keywords.isEmpty()) {
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

        // 1. 키워드별 후보 수집(DB → 벡터(DB존재만) → 외부)
        List<CrawlingProduct> candidates = collectCandidates(
                keywords, minPrice, maxPrice, expectedCount, request, babyContext
        );

        // 2. 재분배(키워드별 2개 보장) + 성별/가격/도메인 가드 반영
        List<CrawlingProduct> balanced = applyFinalFiltersWithRebalance(
                candidates, minPrice, maxPrice, keywords, reqGender,
                expectedCount, PER_KEYWORD_PRIMARY, PER_KEYWORD_BUFFER, babyContext
        );

        // 3. 8개 미만이면 전역 보충(DB 근사→외부)
        if (balanced.size() < TARGET_RESULT_SIZE) {
            balanced = globalTopUp(balanced, keywords, minPrice, maxPrice, reqGender, request, babyContext);
        }

        // 4. DTO 변환(상한 8개)
        List<CrawlingProductResponseDto> items = balanced.stream()
                .limit(TARGET_RESULT_SIZE)
                .map(CrawlingProductResponseDto::from)
                .toList();

        return new CrawlingProductRecommendationResponseDto(items);
    }

    /**
     * 후보 수집
     * - 각 키워드별로 우선 2개 확보: DB → 벡터(DB 존재만) → 외부(네이버)
     * - 그래도 부족하면 전역 풀에서 보충(후단 재분배에서 키워드 균형)
     */
    @Transactional(readOnly = true)
    protected List<CrawlingProduct> collectCandidates(
            List<String> keywords, int minPrice, int maxPrice,
            int targetSize, RecommendationRequestDto request, boolean babyContext) {

        int cap = Math.max(1, targetSize);
        List<CrawlingProduct> acc = new ArrayList<>(cap * 3);
        Set<String> seenKeys = new HashSet<>(); // 제목+이미지 키로 중복 억제
        Set<Long> pickedIds = new HashSet<>();  // DB PK 중복 억제

        Pageable top20 = PageRequest.of(0, 20);

        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            if (acc.size() >= cap * 3) break;

            int needForKw = PER_KEYWORD_PRIMARY;

            // 1. DB: 이름/카테고리 LIKE + 가격 범위
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

            // 2. 벡터: DB에 존재하는 상품만 후보화(리콜 확대)
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
            }

            // 3. 외부(네이버) 페치
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
                }
            }
        }

        // 4. 전역 풀 보충(후단 재분배에서 키워드 균형 처리)
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

            fillWithRulesAnyKeyword(acc, cap * 3, scoredAll, TITLE_SIMILARITY_CUTOFF, seenKeys, pickedIds, keywords);
        }

        return acc;
    }

    /**
     * 최종 재분배
     * - 버킷별 2개 보장 → donor(여분) → overflow 순으로 보충
     * - 성별/가격/도메인 가드 적용
     */
    private List<CrawlingProduct> applyFinalFiltersWithRebalance(
            List<CrawlingProduct> candidates,
            int minPrice, int maxPrice,
            List<String> userKeywords,
            Gender gender, int limit,
            int perKeywordPrimary, int perKeywordBuffer,
            boolean babyContext) {

        Map<String, List<CrawlingProduct>> perKeyword = new LinkedHashMap<>();
        for (String kw : userKeywords) perKeyword.put(kw, new ArrayList<>());

        List<CrawlingProduct> overflow = new ArrayList<>();

        // 후보를 키워드 버킷에 적재(버퍼 초과분은 overflow)
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

        // donor: 각 버킷의 2개 초과분
        List<CrawlingProduct> donors = new ArrayList<>();
        for (List<CrawlingProduct> bucket : perKeyword.values()) {
            if (bucket.size() > perKeywordPrimary) {
                donors.addAll(new ArrayList<>(bucket.subList(perKeywordPrimary, bucket.size())));
            }
        }

        // 부족 버킷에 donor 우선 지급
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

        // 그래도 부족하면 overflow에서 보충
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

        // 최종 결과 조립(키워드 순서 유지)
        List<CrawlingProduct> finalResult = new ArrayList<>(limit);

        // 1) 각 키워드 2개씩 먼저
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

        // 2) 1개짜리 키워드 채우기
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

        // 3) donor → overflow 순으로 보충
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

    /**
     * 전역 보충 단계
     * - DB 풀에서 키워드 코사인 근사 매칭 → 외부 페치
     */
    private List<CrawlingProduct> globalTopUp(
            List<CrawlingProduct> current,
            List<String> keywords,
            int minPrice, int maxPrice,
            Gender gender,
            RecommendationRequestDto request,
            boolean babyContext) {

        List<CrawlingProduct> acc = new ArrayList<>(current);

        // 중복 억제용 키/ID 세트 구성
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

        // DB 풀에서 근사 매칭
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

        // 외부 보충(키워드별 소량 페치)
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

    /** 중복/성별/제목유사 체크를 통과시키며 limit까지 acc에 추가 */
    private void addUntil(List<CrawlingProduct> acc, List<Scored> incoming, int limit,
                          Set<String> seenKeys, Set<Long> haveIds, Gender gender) {
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

    /** 벡터 검색 결과 중 DB에 실제 존재하는 id만 남겨 내부 점수와 함께 정렬 */
    private List<Scored> vectorSimilarFromDB(String keyword,
                                             int minPrice, int maxPrice,
                                             int need,
                                             Set<Long> excludeIds,
                                             RecommendationRequestDto req,
                                             List<String> allKws) {
        String q = buildVectorQuery(
                Optional.ofNullable(req.preference()).orElse(""),
                keyword, req, allKws
        );
        if (q.isBlank()) return List.of();

        String reqAge = Optional.ofNullable(req.age()).orElse(null);
        int topK = Math.max(need * VECTOR_TOPK_MULTIPLIER, 20);

        List<VectorProductSearch.ScoredId> hits;
        try {
            hits = vectorProductSearch.searchWithScores(
                    q, Math.max(minPrice, 0), maxOrMaxInt(maxPrice),
                    reqAge, null, topK, VECTOR_THRESHOLD_DEFAULT
            );
        } catch (Exception e) {
            log.warn("vector similar search failed: kw={}, err={}", keyword, e.toString());
            return List.of();
        }
        if (hits == null || hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream()
                .map(VectorProductSearch.ScoredId::productId)
                .filter(Objects::nonNull)
                .filter(id -> !excludeIds.contains(id))
                .toList();
        if (ids.isEmpty()) return List.of();

        Map<Long, Double> scoreMap = hits.stream()
                .collect(Collectors.toMap(
                        VectorProductSearch.ScoredId::productId,
                        VectorProductSearch.ScoredId::score,
                        (a, b) -> a
                ));

        List<CrawlingProduct> loaded = new ArrayList<>();
        crawlingProductRepository.findAllById(ids).forEach(loaded::add);

        return loaded.stream()
                .map(p -> new Scored(p, scoreMap.getOrDefault(p.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(Scored::s).reversed())
                .toList();
    }

    /** DB LIKE + 가격 범위 조회(레포지토리 커스텀 메서드 사용 가정) */
    private List<CrawlingProduct> loadFromDbByNameOrCategory(String keyword, int minPrice, int maxPrice, Pageable top20) {
        String kw = keyword.trim();
        int min = Math.max(minPrice, 0);
        int max = maxOrMaxInt(maxPrice);
        List<CrawlingProduct> list = crawlingProductRepository
                .findTopByNameOrCategoryLikeWithinPrice(kw, min, max, top20);
        return new ArrayList<>(list.subList(0, Math.min(20, list.size())));
    }

    /** 외부(네이버) 페치: 실패 시 빈 리스트 반환 */
    private List<CrawlingProduct> loadFromNaverByKeyword(String kw, int need,
                                                         int minPrice, int maxPrice,
                                                         RecommendationRequestDto request) {
        if (need <= 0) return List.of();
        try {
            return crawlingProductImportService.fetchForKeyword(
                    kw, minPrice, maxPrice,
                    Optional.ofNullable(request.age()).orElse(""),
                    Optional.ofNullable(request.reason()).orElse(""),
                    Optional.ofNullable(request.preference()).orElse(""),
                    need
            );
        } catch (Exception e) {
            log.warn("naver fetch fail kw={}", kw, e);
            return List.of();
        }
    }

    /**
     * 특정 키워드 슬롯(kw)에만 넣을 수 있도록 제한하는 적재기
     * - 제목 유사 중복 제거
     * - PK 중복 제거
     */
    private int fillWithRulesLimitedForKeyword(
            List<CrawlingProduct> acc, int cap, int quotaForThisKeyword, List<Scored> scored,
            String keywordForThisSlot,
            double titleJacCutoff, Set<String> seenKeys, Set<Long> pickedIds) {

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

    /** 전역 보충 적재기(후단 재분배에서 키워드 균형 보정) */
    private void fillWithRulesAnyKeyword(
            List<CrawlingProduct> acc, int cap, List<Scored> scored,
            double titleJacCutoff, Set<String> seenKeys, Set<Long> pickedIds,
            List<String> userKws) {

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

    /** 요청 컨텍스트에 유아/아기 맥락이 포함되면 true */
    private boolean isBabyContext(RecommendationRequestDto req) {
        String pref = Optional.ofNullable(req.preference()).orElse("");
        String reason = Optional.ofNullable(req.reason()).orElse("");
        String age = Optional.ofNullable(req.age()).orElse("");
        return containsAnyIgnoreCase(pref, "출산", "육아", "베이비", "유아", "영유아")
                || containsAnyIgnoreCase(reason, "출산", "출산선물", "돌잔치", "백일")
                || containsAnyIgnoreCase(age, "영유아", "유아", "아기");
    }

    /** 상품이 유아/아기 도메인에 속하면 true */
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

    /**
     * 키워드 매칭 판정:
     * 1) 제목/태그/카테고리 포함 → 2) 벡터 스토어로 강한 보조 → 3) 토큰 코사인으로 약한 보조
     */
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

        // 벡터 스토어 확인(강)
        if (p.getId() != null && vectorConfirmsMatch(p.getId(), k)) return true;

        // 토큰 코사인 보조(약)
        double tokenCos = cosineKeywordSimilarity(k, p);
        return tokenCos >= COSINE_SIM_THRESHOLD;
    }

    /** 사용자 키워드 중 하나라도 매칭되면 true */
    private boolean matchesAnyUserKeyword(CrawlingProduct p, List<String> userKws) {
        if (p == null || userKws == null || userKws.isEmpty()) return false;
        for (String kw : userKws) {
            if (keywordMatches(p, kw)) return true;
        }
        return false;
    }

    /** 매칭된 사용자 키워드를 모두 수집(재분배 시 버킷 배치용) */
    private List<String> findMatchedKeywords(CrawlingProduct p, List<String> userKws) {
        List<String> matched = new ArrayList<>();
        if (p == null || userKws == null || userKws.isEmpty()) return matched;
        for (String kw : userKws) {
            if (keywordMatches(p, kw)) matched.add(kw);
        }
        return matched;
    }

    /** 벡터 스토어가 특정 키워드-상품 매칭을 상위 점수로 확인해 주면 true */
    private boolean vectorConfirmsMatch(Long productId, String keyword) {
        try {
            String q = "키워드:" + keyword;
            int topK = 30;
            List<VectorProductSearch.ScoredId> hits = vectorProductSearch.searchWithScores(
                    q, 0, Integer.MAX_VALUE, null, null, topK, VECTOR_THRESHOLD_DEFAULT
            );
            if (hits == null || hits.isEmpty()) return false;

            for (VectorProductSearch.ScoredId h : hits) {
                if (Objects.equals(h.productId(), productId) && h.score() >= VECTOR_THRESHOLD_DEFAULT) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("vectorConfirmsMatch fail: kw={}, id={}, err={}", keyword, productId, e.toString());
        }
        return false;
    }

    /** 가격 범위 체크 */
    private static boolean withinPrice(CrawlingProduct p, int minPrice, int maxPrice) {
        int price = Optional.ofNullable(p.getPrice()).orElse(0);
        if (minPrice > 0 && price < minPrice) return false;
        if (maxPrice > 0 && price > maxPrice) return false;
        return true;
    }

    /** maxPrice가 0(무제한)이면 Integer.MAX_VALUE로 치환 */
    private static int maxOrMaxInt(int maxPrice) {
        return (maxPrice > 0) ? maxPrice : Integer.MAX_VALUE;
    }

    /** 키워드 정규화(트림 + 중복 제거 + 상한) */
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

    /** 간단 토크나이저(영/숫/한글 유지, 나머지 공백) */
    private static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        String norm = s.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-zA-Z가-힣]", " ");
        String[] arr = norm.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String w : arr) if (!w.isBlank()) out.add(w);
        return out;
    }

    /** 벡터 쿼리 빌더(핵심/보조 키워드 + 힌트) */
    private String buildVectorQuery(String preference, String baseKw,
                                    RecommendationRequestDto req, List<String> allKws) {
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

    /** 키워드 vs 상품 토큰 코사인 근사(교집합/ |A|*|B| 의 제곱근) */
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

    /** 같은 상품 판정: (id 우선) → productUrl 백업 */
    private boolean sameProduct(CrawlingProduct a, CrawlingProduct b) {
        if (a == null || b == null) return false;
        Long aId = a.getId();
        Long bId = b.getId();
        if (aId != null && bId != null) return aId.equals(bId);
        String aUrl = Optional.ofNullable(a.getProductUrl()).orElse("").trim();
        String bUrl = Optional.ofNullable(b.getProductUrl()).orElse("").trim();
        return !aUrl.isEmpty() && aUrl.equals(bUrl);
    }

    /** 리스트 내 중복 여부 */
    private boolean alreadyContains(List<CrawlingProduct> list, CrawlingProduct p) {
        if (list == null || p == null) return false;
        for (CrawlingProduct ex : list) if (sameProduct(ex, p)) return true;
        return false;
    }

    /** 버킷 내 중복 여부 */
    private boolean alreadyContainsBucket(List<CrawlingProduct> bucket, CrawlingProduct p) {
        if (bucket == null || p == null) return false;
        for (CrawlingProduct ex : bucket) if (sameProduct(ex, p)) return true;
        return false;
    }

    /** 부분 문자열 포함(대소문자 무시) */
    private boolean containsAnyIgnoreCase(String src, String... needles) {
        if (src == null || src.isBlank()) return false;
        String s = src.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isBlank() && s.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 게스트 존재 검증 */
    private Guest existsGuest(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("게스트 조회 실패: guestId={}", id);
                    return new ErrorException(ExceptionEnum.GUEST_NOT_FOUND);
                });
    }

    /** 추천 세션 존재 검증 */
    private RecommendationSession existsRecommendationSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("추천 세션 조회 실패: sessionId={}", id);
                    return new ErrorException(ExceptionEnum.SESSION_NOT_FOUND);
                });
    }

    /** 세션-게스트 소유권 검증 */
    private static void verifySessionOwner(RecommendationSession session, Guest guest) {
        if (!session.getGuest().getId().equals(guest.getId())) {
            log.error("세션 접근 권한 오류 | sessionId={}, guestId={}", session.getId(), guest.getId());
            throw new ErrorException(ExceptionEnum.SESSION_FORBIDDEN);
        }
    }
}
