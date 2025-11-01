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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 벡터 기반 선물 추천 서비스
 * - 키워드당 먼저 2개씩 채우고
 * - 많이 나온 키워드에서 못 채운 키워드로 1개씩 빌려주고
 * - 그래도 부족하면 남는 걸로 8개 채운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class RecommendationVectorService {
    /** 최종으로 내려줄 개수 */
    private static final int TARGET_RESULT_SIZE = 8;

    /** 키워드당 기본으로 보장하고 싶은 개수 */
    private static final int PER_KEYWORD_PRIMARY = 2;

    /** 키워드당 임시로 쌓아둘 수 있는 최대 개수 (여기서 남는 걸 다른 키워드에 빌려줌) */
    private static final int PER_KEYWORD_BUFFER = 4;

    /** DB/벡터/풀에서 긁어올 때 한번에 모아둘 최대 후보 수 */
    private static final int CANDIDATE_POOL_LIMIT = 1200;

    /** 벡터 유사도 임계값 */
    private static final double VECTOR_THRESHOLD_DEFAULT = 0.78;

    /** 벡터 topK 배수 */
    private static final int VECTOR_TOPK_MULTIPLIER = 4;

    /** 제목 중복을 판단할 때의 자카드 컷오프 */
    private static final double TITLE_SIMILARITY_CUTOFF = 0.85;

    /** 판매자당 최대 개수 (동일 스토어에서만 쭉 안 나오게) */
    private static final int MAX_PER_SELLER = 2;

    /** 한 번에 받을 수 있는 키워드 수 제한 */
    private static final int MAX_KEYWORDS = 10;

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final VectorProductSearch vectorProductSearch;
    private final CrawlingProductRepository crawlingProductRepository;
    private final CrawlingProductImportService crawlingProductImportService;

    private record Scored(CrawlingProduct p, double s) {}

    @Transactional
    public CrawlingProductRecommendationResponseDto recommendByVector(UUID guestId,
                                                                      UUID sessionId,
                                                                      RecommendationRequestDto request) {

        Guest guest = existsGuest(guestId);
        RecommendationSession session = existsRecommendationSession(sessionId);
        verifySessionOwner(session, guest);

        // 1. 키워드 정리 (중복 제거 + 최대 10개)
        List<String> keywords = normalizeKeywords(request.keywords());
        if (keywords.isEmpty()) {
            return new CrawlingProductRecommendationResponseDto(List.of());
        }

        int minPrice = request.minPrice();
        int maxPrice = request.maxPrice();
        Gender reqGender = request.gender();

        int expectedCount = Math.min(TARGET_RESULT_SIZE, keywords.size() * PER_KEYWORD_PRIMARY);

        // 2. 후보 수집
        List<CrawlingProduct> candidates = collectCandidates(
                keywords,
                minPrice,
                maxPrice,
                expectedCount,
                request
        );

        // 3. 최종 분배 (여기가 핵심)
        List<CrawlingProduct> balanced = applyFinalFiltersWithRebalance(
                candidates,
                minPrice,
                maxPrice,
                keywords,
                reqGender,
                expectedCount,
                PER_KEYWORD_PRIMARY,
                PER_KEYWORD_BUFFER
        );

        // 4. 그래도 부족하면 느슨하게 fetch해서 다시 분배
        if (balanced.size() < expectedCount) {
            balanced = topUpIfNeeded(
                    balanced,
                    keywords,
                    minPrice,
                    maxPrice,
                    reqGender,
                    expectedCount,
                    request
            );
        }

        // 5. DTO 변환
        List<CrawlingProductResponseDto> items = balanced.stream()
                .limit(expectedCount)
                .map(CrawlingProductResponseDto::from)
                .toList();

        return new CrawlingProductRecommendationResponseDto(items);
    }

    /**
     * 후보 수집: DB → 벡터 → 외부 → 전체 풀
     */
    @Transactional(readOnly = true)
    protected List<CrawlingProduct> collectCandidates(List<String> keywords,
                                                      int minPrice,
                                                      int maxPrice,
                                                      int targetSize,
                                                      RecommendationRequestDto request) {

        int cap = Math.max(1, targetSize);
        String preference = Optional.ofNullable(request.preference()).orElse("");
        String age = Optional.ofNullable(request.age()).orElse("");
        String reason = Optional.ofNullable(request.reason()).orElse("");

        List<CrawlingProduct> acc = new ArrayList<>(cap * 3);
        Set<String> seenKeys = new HashSet<>();
        Map<String, Integer> sellerCount = new HashMap<>();
        Set<Long> pickedIds = new HashSet<>();

        Map<String, List<CrawlingProduct>> localDbCache = new HashMap<>();

        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            if (acc.size() >= cap * 3) break;

            // 1. DB
            List<CrawlingProduct> dbCandidates = localDbCache.computeIfAbsent(
                    kw,
                    k -> loadFromDbByKeywordStrict(k, minPrice, maxPrice)
            );

            List<Scored> scoredDb = dbCandidates.stream()
                    .filter(p -> matchesAnyUserKeyword(p, keywords))
                    .map(p -> new Scored(p, 10.0))
                    .toList();

            fillWithRulesLimited(
                    acc, cap * 3, PER_KEYWORD_PRIMARY, scoredDb,
                    TITLE_SIMILARITY_CUTOFF, MAX_PER_SELLER, seenKeys, sellerCount, pickedIds,
                    preference, age, reason, keywords
            );

            // 2. 벡터
            if (acc.size() < cap * 3) {
                List<CrawlingProduct> vec = vectorCandidatesForKeywordPreferTitle(
                        kw,
                        PER_KEYWORD_PRIMARY,
                        minPrice,
                        maxPrice,
                        pickedIds,
                        preference,
                        request,
                        keywords
                );

                if (!vec.isEmpty()) {
                    List<Scored> scoredVec = vec.stream()
                            .map(p -> {
                                String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
                                boolean hardMatch = title != null && title.toLowerCase(Locale.ROOT)
                                        .contains(kw.toLowerCase(Locale.ROOT));
                                return new Scored(p, hardMatch ? 8.0 : 4.0);
                            })
                            .toList();

                    fillWithRulesLimited(
                            acc, cap * 3, PER_KEYWORD_PRIMARY, scoredVec,
                            TITLE_SIMILARITY_CUTOFF, MAX_PER_SELLER, seenKeys, sellerCount, pickedIds,
                            preference, age, reason, keywords
                    );
                }
            }

            // 3. 외부 (네이버)
            if (acc.size() < cap * 3) {
                List<CrawlingProduct> fetched = loadFromNaverByKeyword(
                        kw, PER_KEYWORD_PRIMARY, minPrice, maxPrice, request
                );

                if (!fetched.isEmpty()) {
                    List<Scored> scoredFetched = fetched.stream()
                            .filter(p -> matchesAnyUserKeyword(p, keywords))
                            .map(p -> new Scored(p, 1.0))
                            .toList();

                    fillWithRulesLimited(
                            acc, cap * 3, PER_KEYWORD_PRIMARY, scoredFetched,
                            TITLE_SIMILARITY_CUTOFF, MAX_PER_SELLER, seenKeys, sellerCount, pickedIds,
                            preference, age, reason, keywords
                    );
                }
            }
        }

        // 4. 그래도 모자라면 전체 풀
        if (acc.size() < cap) {
            List<CrawlingProduct> pool;
            try {
                pool = crawlingProductRepository.findTop500ByPriceBetweenOrderByIdDesc(
                        Math.max(minPrice, 0), maxOrMaxInt(maxPrice)
                );
            } catch (Exception e) {
                pool = crawlingProductRepository.findAll();
            }

            if (pool.size() > CANDIDATE_POOL_LIMIT) {
                pool = pool.subList(0, CANDIDATE_POOL_LIMIT);
            }

            List<Scored> scoredAll = new ArrayList<>();
            for (CrawlingProduct p : pool) {
                if (!withinPrice(p, minPrice, maxPrice)) continue;
                if (!matchesAnyUserKeyword(p, keywords)) continue;
                double score = domainAlignmentBoost(p, preference);
                scoredAll.add(new Scored(p, score));
            }
            scoredAll.sort(Comparator.comparingDouble(Scored::s).reversed());

            fillWithRules(
                    acc, cap * 3, scoredAll,
                    TITLE_SIMILARITY_CUTOFF, MAX_PER_SELLER,
                    seenKeys, sellerCount, pickedIds,
                    preference, age, reason, keywords
            );
        }

        return acc;
    }

    /**
     * 핵심: 키워드별 2개 → 남는 키워드에서 부족한 키워드로 재분배 → 그래도 모자라면 남는 걸로 채움
     */
    private List<CrawlingProduct> applyFinalFiltersWithRebalance(List<CrawlingProduct> candidates,
                                                                 int minPrice,
                                                                 int maxPrice,
                                                                 List<String> userKeywords,
                                                                 Gender gender,
                                                                 int limit,
                                                                 int perKeywordPrimary,
                                                                 int perKeywordBuffer) {

        Map<String, List<CrawlingProduct>> perKeyword = new LinkedHashMap<>();
        for (String kw : userKeywords) {
            perKeyword.put(kw, new ArrayList<>());
        }

        List<CrawlingProduct> overflow = new ArrayList<>();

        for (CrawlingProduct p : candidates) {
            if (!withinPrice(p, minPrice, maxPrice)) continue;

            if (RecommendationUtil.blockedByGender(gender, p)) continue;

            List<String> matched = findMatchedKeywords(p, userKeywords);
            if (matched.isEmpty()) continue;

            boolean stored = false;
            for (String kw : matched) {
                List<CrawlingProduct> bucket = perKeyword.get(kw);
                if (bucket == null) continue;
                if (bucket.size() < perKeywordBuffer) {
                    bucket.add(p);
                    stored = true;
                    break;
                }
            }
            if (!stored) {
                overflow.add(p);
            }
        }
        // 2) 여분이 있는 키워드의 3,4번째 아이템을 donor로 모은다
        List<CrawlingProduct> donors = new ArrayList<>();
        for (Map.Entry<String, List<CrawlingProduct>> e : perKeyword.entrySet()) {
            List<CrawlingProduct> bucket = e.getValue();
            if (bucket.size() > perKeywordPrimary) {
                donors.addAll(bucket.subList(perKeywordPrimary, bucket.size()));
            }
        }

        // 3) 2개가 안 된 키워드에 donor를 우선적으로 준다
        // 여기서는 "매칭되는 donor만"이 아니라 "그냥 많이 나온 키워드에서 빌려온다"로 바꾼다.
        for (Map.Entry<String, List<CrawlingProduct>> e : perKeyword.entrySet()) {
            List<CrawlingProduct> bucket = e.getValue();
            if (bucket.size() >= perKeywordPrimary) continue;

            Iterator<CrawlingProduct> donorIt = donors.iterator();
            while (bucket.size() < perKeywordPrimary && donorIt.hasNext()) {
                CrawlingProduct d = donorIt.next();
                if (alreadyContainsBucket(bucket, d)) {
                    donorIt.remove();
                    continue;
                }
                bucket.add(d);
                donorIt.remove();
            }
        }

        // 4) 그래도 2개가 안 된 키워드는 overflow에서 채운다 (여기서부터는 유사 카테고리가 아닐 수도 있음)
        for (Map.Entry<String, List<CrawlingProduct>> e : perKeyword.entrySet()) {
            List<CrawlingProduct> bucket = e.getValue();
            if (bucket.size() >= perKeywordPrimary) continue;

            Iterator<CrawlingProduct> ovIt = overflow.iterator();
            while (bucket.size() < perKeywordPrimary && ovIt.hasNext()) {
                CrawlingProduct o = ovIt.next();
                if (alreadyContainsBucket(bucket, o)) {
                    ovIt.remove();
                    continue;
                }
                bucket.add(o);
                ovIt.remove();
            }
        }

        // 5) 최종 결과 만들기
        List<CrawlingProduct> finalResult = new ArrayList<>(limit);

        // 1. 키워드 순서대로 "최대 perKeywordPrimary(=2)개씩"만 넣는다
        for (String kw : userKeywords) {
            List<CrawlingProduct> bucket = perKeyword.get(kw);
            if (bucket == null || bucket.isEmpty()) continue;

            int take = Math.min(perKeywordPrimary, bucket.size());
            for (int i = 0; i < take; i++) {
                CrawlingProduct p = bucket.get(i);
                if (finalResult.size() >= limit) break;
                if (alreadyContains(finalResult, p)) continue;
                finalResult.add(p);
            }
            if (finalResult.size() >= limit) break;
        }

        // 2. 그래도 모자라면 donor 남은 것들로 채운다
        if (finalResult.size() < limit) {
            for (CrawlingProduct d : donors) {
                if (finalResult.size() >= limit) break;
                if (alreadyContains(finalResult, d)) continue;
                finalResult.add(d);
            }
        }

        // 3. 그래도 모자라면 overflow로 채운다
        if (finalResult.size() < limit) {
            for (CrawlingProduct o : overflow) {
                if (finalResult.size() >= limit) break;
                if (alreadyContains(finalResult, o)) continue;
                finalResult.add(o);
            }
        }

        return finalResult.size() > limit ? finalResult.subList(0, limit) : finalResult;
    }

    private boolean alreadyContains(List<CrawlingProduct> list, CrawlingProduct p) {
        for (CrawlingProduct existing : list) {
            if (sameProduct(existing, p)) return true;
        }
        return false;
    }

    private boolean alreadyContainsBucket(List<CrawlingProduct> bucket, CrawlingProduct p) {
        for (CrawlingProduct existing : bucket) {
            if (sameProduct(existing, p)) return true;
        }
        return false;
    }

    private boolean sameProduct(CrawlingProduct a, CrawlingProduct b) {
        if (a == null || b == null) return false;
        if (a.getId() != null && b.getId() != null) {
            return a.getId().equals(b.getId());
        }
        return Objects.equals(a.getProductUrl(), b.getProductUrl());
    }

    /**
     * 느슨하게 더 가져와서 다시 같은 로직으로 분배
     */
    private List<CrawlingProduct> topUpIfNeeded(List<CrawlingProduct> current,
                                                List<String> keywords,
                                                int minPrice,
                                                int maxPrice,
                                                Gender gender,
                                                int targetSize,
                                                RecommendationRequestDto request) {

        if (current.size() >= targetSize) {
            return current;
        }

        int remain = targetSize - current.size();
        List<CrawlingProduct> extra = new ArrayList<>();

        int looseMin = Math.max(0, minPrice - (int) (minPrice * 0.15));
        int looseMax = (maxPrice > 0) ? (int) (maxPrice * 1.15) : 0;

        for (String kw : keywords) {
            if (extra.size() >= remain * 2) break;
            try {
                List<CrawlingProduct> fetched = crawlingProductImportService.fetchForKeyword(
                        kw,
                        looseMin,
                        looseMax,
                        Optional.ofNullable(request.age()).orElse(""),
                        Optional.ofNullable(request.reason()).orElse(""),
                        Optional.ofNullable(request.preference()).orElse(""),
                        Math.max(2, remain)
                );
                extra.addAll(fetched);
            } catch (Exception e) {
                log.warn("loose fetch fail kw={}", kw, e);
            }
        }

        if (extra.isEmpty()) {
            return current;
        }

        current.addAll(extra);
        return applyFinalFiltersWithRebalance(
                current,
                minPrice,
                maxPrice,
                keywords,
                gender,
                targetSize,
                PER_KEYWORD_PRIMARY,
                PER_KEYWORD_BUFFER
        );
    }

    private List<String> findMatchedKeywords(CrawlingProduct p, List<String> userKws) {
        List<String> matched = new ArrayList<>();
        if (p == null || userKws == null || userKws.isEmpty()) return matched;

        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
        String titleLower = Optional.ofNullable(title).orElse("").toLowerCase(Locale.ROOT);
        List<String> tags = Optional.ofNullable(p.getKeywords()).orElse(List.of());

        for (String kw : userKws) {
            if (kw == null || kw.isBlank()) continue;
            String k = kw.toLowerCase(Locale.ROOT);
            boolean inTitle = titleLower.contains(k);
            boolean inTags = tags.stream()
                    .anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(k));
            if (inTitle || inTags) {
                matched.add(kw);
            }
        }
        return matched;
    }

    private List<CrawlingProduct> loadFromDbByKeywordStrict(String keyword,
                                                            int minPrice,
                                                            int maxPrice) {
        String kw = keyword.trim();
        List<CrawlingProduct> titleMatched = crawlingProductRepository
                .findTop20ByDisplayNameContainingIgnoreCaseAndPriceBetweenOrderByIdDesc(
                        kw,
                        Math.max(minPrice, 0),
                        maxOrMaxInt(maxPrice)
                );
        return titleMatched.stream()
                .limit(10)
                .toList();
    }

    private List<CrawlingProduct> loadFromNaverByKeyword(String kw,
                                                         int need,
                                                         int minPrice,
                                                         int maxPrice,
                                                         RecommendationRequestDto request) {
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
            log.warn("naver fetch fail kw={}", kw, e);
            return List.of();
        }
    }

    private List<CrawlingProduct> vectorCandidatesForKeywordPreferTitle(
            String keyword,
            int need,
            int minPrice,
            int maxPrice,
            Set<Long> excludeIds,
            String preference,
            RecommendationRequestDto req,
            List<String> allKws
    ) {
        if (need <= 0) return Collections.emptyList();

        String q = buildVectorQuery(preference, keyword, req, allKws);
        if (q.isBlank()) return Collections.emptyList();

        String reqAge = Optional.ofNullable(req.age()).orElse(null);

        int topK = Math.max(need * VECTOR_TOPK_MULTIPLIER, 10);
        List<VectorProductSearch.ScoredId> scoredIds;
        try {
            scoredIds = vectorProductSearch.searchWithScores(
                    q,
                    Math.max(minPrice, 0),
                    maxOrMaxInt(maxPrice),
                    reqAge,
                    null,
                    topK,
                    VECTOR_THRESHOLD_DEFAULT
            );
        } catch (Exception ex) {
            log.warn("vector search failed: q={}, err={}", q, ex.toString());
            return Collections.emptyList();
        }
        if (scoredIds == null || scoredIds.isEmpty()) return Collections.emptyList();

        List<Long> ids = scoredIds.stream()
                .map(VectorProductSearch.ScoredId::productId)
                .filter(Objects::nonNull)
                .filter(id -> !excludeIds.contains(id))
                .toList();
        if (ids.isEmpty()) return Collections.emptyList();

        List<CrawlingProduct> loaded = new ArrayList<>();
        crawlingProductRepository.findAllById(ids).forEach(loaded::add);

        Map<Long, CrawlingProduct> byId = loaded.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CrawlingProduct::getId, p -> p, (a, b) -> a));

        String kwLower = keyword.toLowerCase(Locale.ROOT);
        List<CrawlingProduct> hardMatched = new ArrayList<>();
        List<CrawlingProduct> others = new ArrayList<>();

        for (VectorProductSearch.ScoredId sid : scoredIds) {
            Long pid = sid.productId();
            CrawlingProduct p = pid == null ? null : byId.get(pid);
            if (p == null) continue;
            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            if (title != null && title.toLowerCase(Locale.ROOT).contains(kwLower)) {
                hardMatched.add(p);
            } else {
                others.add(p);
            }
        }

        List<CrawlingProduct> result = new ArrayList<>();
        result.addAll(hardMatched);
        result.addAll(others);

        return result.size() > need ? result.subList(0, need) : result;
    }

    private int fillWithRulesLimited(List<CrawlingProduct> acc,
                                     int cap,
                                     int quotaForThisKeyword,
                                     List<Scored> scored,
                                     double titleJacCutoff,
                                     int maxPerSeller,
                                     Set<String> seenKeys,
                                     Map<String, Integer> sellerCount,
                                     Set<Long> pickedIds,
                                     String preference,
                                     String age,
                                     String reason,
                                     List<String> userKws) {

        int added = 0;
        for (Scored sc : scored) {
            if (acc.size() >= cap) break;
            if (added >= quotaForThisKeyword) break;

            CrawlingProduct p = sc.p();
            Long id = p.getId();
            if (id != null && pickedIds.contains(id)) continue;
            if (!matchesAnyUserKeyword(p, userKws)) continue;

            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");
            String seller = Optional.ofNullable(p.getSellerName()).orElse("").trim().toLowerCase(Locale.ROOT);

            // 제목 중복 체크
            boolean dupTitle = false;
            for (String existing : seenKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= titleJacCutoff) { dupTitle = true; break; }
            }
            if (dupTitle) continue;

            // 판매자 제한
            if (!seller.isBlank()) {
                int cnt = sellerCount.getOrDefault(seller, 0);
                if (cnt >= maxPerSeller) continue;
                sellerCount.put(seller, cnt + 1);
            }

            seenKeys.add(key);
            acc.add(p);
            if (id != null) pickedIds.add(id);
            added++;
        }
        return added;
    }

    private void fillWithRules(List<CrawlingProduct> acc,
                               int cap,
                               List<Scored> scored,
                               double titleJacCutoff,
                               int maxPerSeller,
                               Set<String> seenKeys,
                               Map<String, Integer> sellerCount,
                               Set<Long> pickedIds,
                               String preference,
                               String age,
                               String reason,
                               List<String> userKws) {

        for (Scored sc : scored) {
            if (acc.size() >= cap) break;

            CrawlingProduct p = sc.p();
            Long id = p.getId();
            if (id != null && pickedIds.contains(id)) continue;
            if (!matchesAnyUserKeyword(p, userKws)) continue;

            String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
            String baseTitle = RecommendationUtil.extractBaseTitle(title);
            String key = baseTitle + "::" + Optional.ofNullable(p.getImageUrl()).orElse("");
            String seller = Optional.ofNullable(p.getSellerName()).orElse("").trim().toLowerCase(Locale.ROOT);

            boolean dupTitle = false;
            for (String existing : seenKeys) {
                String exTitle = existing.split("::", 2)[0];
                double jac = RecommendationUtil.jaccardSimilarityByWords(exTitle, baseTitle);
                if (jac >= titleJacCutoff) { dupTitle = true; break; }
            }
            if (dupTitle) continue;

            if (!seller.isBlank()) {
                int cnt = sellerCount.getOrDefault(seller, 0);
                if (cnt >= maxPerSeller) continue;
                sellerCount.put(seller, cnt + 1);
            }

            seenKeys.add(key);
            acc.add(p);
            if (id != null) pickedIds.add(id);
        }
    }

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
        return set.stream().limit(MAX_KEYWORDS).toList();
    }

    private static boolean matchesAnyUserKeyword(CrawlingProduct p, List<String> userKws) {
        if (userKws == null || userKws.isEmpty() || p == null) return false;
        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());
        String titleLower = Optional.ofNullable(title).orElse("").toLowerCase(Locale.ROOT);
        List<String> tags = Optional.ofNullable(p.getKeywords()).orElse(List.of());

        for (String kw : userKws) {
            if (kw == null || kw.isBlank()) continue;
            String k = kw.toLowerCase(Locale.ROOT);
            boolean inTitle = titleLower.contains(k);
            boolean inTags = tags.stream()
                    .anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(k));
            if (inTitle || inTags) return true;
        }
        return false;
    }

    private String buildVectorQuery(String preference, String baseKw, RecommendationRequestDto req, List<String> allKws) {
        String kw = Optional.ofNullable(baseKw).orElse("");
        String others = allKws.stream()
                .filter(k -> k != null && !k.equals(baseKw))
                .limit(4)
                .collect(Collectors.joining(", "));
        String rel = Optional.ofNullable(req.relation()).orElse("");
        String age = Optional.ofNullable(req.age()).orElse("");
        String reason = Optional.ofNullable(req.reason()).orElse("");
        String pref = Optional.ofNullable(preference).orElse("");

        return String.format(
                "핵심키워드:[%s]; 보조키워드:[%s]. 힌트(무시 가능): relation=%s, age=%s, reason=%s, preference=%s",
                kw, others, rel, age, reason, pref
        );
    }

    private double domainAlignmentBoost(CrawlingProduct p, String preference) {
        String title = Optional.ofNullable(p.getDisplayName()).orElse(p.getOriginalName());

        boolean babyInTitle = RecommendationUtil.isBabyKeywordIncluded(title);
        boolean babyInTags = Optional.ofNullable(p.getKeywords()).orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(RecommendationUtil::isBabyKeywordIncluded);
        boolean babyLike = babyInTitle || babyInTags;

        boolean babyPref = containsAnyIgnoreCase(preference, "출산", "육아", "유아", "영유아", "아기", "베이비");

        if (babyPref && babyLike) return 1.2;
        if (babyPref && !babyLike) return 0.4;
        if (!babyPref && babyLike) return -0.2;
        return 0.0;
    }

    private boolean containsAnyIgnoreCase(String src, String... needles) {
        if (src == null || src.isBlank()) return false;
        String s = src.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isBlank() && s.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

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
