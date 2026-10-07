package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.BulkStatus;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.enums.ProductSort;
import com.example.giftrecommender.domain.repository.CrawlingProductQueryRepository;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.*;
import com.example.giftrecommender.dto.request.age.AgeBulkRequestDto;
import com.example.giftrecommender.dto.request.age.AgeRequestDto;
import com.example.giftrecommender.dto.request.confirm.ConfirmBulkRequestDto;
import com.example.giftrecommender.dto.request.confirm.ConfirmRequestDto;
import com.example.giftrecommender.dto.request.gender.GenderBulkRequestDto;
import com.example.giftrecommender.dto.request.gender.GenderRequestDto;
import com.example.giftrecommender.dto.request.product.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.request.product.CrawlingProductUpdateRequestDto;
import com.example.giftrecommender.dto.request.product.ProductKeywordBulkSaveRequest;
import com.example.giftrecommender.dto.request.product.ProductKeywordBulkUpdateRequest;
import com.example.giftrecommender.dto.response.*;
import com.example.giftrecommender.dto.response.age.AgeBulkResponseDto;
import com.example.giftrecommender.dto.response.age.AgeResponseDto;
import com.example.giftrecommender.dto.response.confirm.ConfirmBulkResponseDto;
import com.example.giftrecommender.dto.response.confirm.ConfirmResponseDto;
import com.example.giftrecommender.dto.response.gender.GenderBulkResponseDto;
import com.example.giftrecommender.dto.response.gender.GenderResponseDto;
import com.example.giftrecommender.dto.response.product.*;
import com.example.giftrecommender.mapper.CrawlingProductMapper;
import com.example.giftrecommender.vector.ProductVectorService;
import com.example.giftrecommender.vector.VectorProductSearch;
import com.example.giftrecommender.vector.event.ProductDeletedEvent;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingProductService {

    private final CrawlingProductRepository crawlingProductRepository;
    private final CrawlingProductQueryRepository crawlingProductQueryRepository;
    private final CrawlingProductSaver crawlingProductSaver;
    private final ObjectProvider<ProductVectorService> productVectorServiceProvider;
    private final ObjectProvider<VectorProductSearch> vectorProductSearchProvider;
    private final ApplicationEventPublisher eventPublisher;

    // 벡터 후보 풀 크기
    private static final int SIMILARITY_CANDIDATE_LIMIT = 80;

    // 검색용 similarity threshold
    private static final double SIMILARITY_THRESHOLD = 0.7;

    /*
     * 여러건 저장
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CrawlingProductBulkSaveResponseDto saveAll(List<CrawlingProductRequestDto> requestDtoList) {
        int success = 0, duplicated = 0, failed = 0;
        List<BulkItemResultDto> results = new ArrayList<>();

        for (CrawlingProductRequestDto dto : requestDtoList) {
            try {
                CrawlingProductResponseDto savedDto = crawlingProductSaver.save(dto);

                results.add(new BulkItemResultDto(
                        dto.productUrl(),
                        BulkStatus.SUCCESS,
                        null,
                        null,
                        savedDto.id(),
                        savedDto
                ));
                success++;

            } catch (TransactionSystemException tse) {
                Throwable root = NestedExceptionUtils.getMostSpecificCause(tse);

                if (root instanceof ConstraintViolationException cve) {
                    results.add(new BulkItemResultDto(
                            dto.productUrl(),
                            BulkStatus.FAILED,
                            "VALIDATION_ERROR",
                            joinViolationMsgs(cve),
                            null,
                            null
                    ));
                    failed++;
                } else if (root instanceof SQLIntegrityConstraintViolationException
                        || containsDuplicateKeyword(root != null ? root.getMessage() : null)) {
                    results.add(new BulkItemResultDto(
                            dto.productUrl(),
                            BulkStatus.DUPLICATED,
                            "DUPLICATE_KEY",
                            "이미 존재하는 URL",
                            null,
                            null
                    ));
                    duplicated++;
                } else {
                    results.add(new BulkItemResultDto(
                            dto.productUrl(),
                            BulkStatus.FAILED,
                            "TRANSACTION_ERROR",
                            "트랜잭션 처리 중 오류가 발생했습니다",
                            null,
                            null
                    ));
                    failed++;
                }

            } catch (DataIntegrityViolationException dive) {
                if (isUniqueViolation(dive)) {
                    results.add(new BulkItemResultDto(
                            dto.productUrl(),
                            BulkStatus.DUPLICATED,
                            "DUPLICATE_KEY",
                            "이미 존재하는 URL",
                            null,
                            null
                    ));
                    duplicated++;
                } else {
                    results.add(new BulkItemResultDto(
                            dto.productUrl(),
                            BulkStatus.FAILED,
                            "INTEGRITY_VIOLATION",
                            "데이터 무결성 제약 위반",
                            null,
                            null
                    ));
                    failed++;
                }

            } catch (ConstraintViolationException cve) {
                results.add(new BulkItemResultDto(
                        dto.productUrl(),
                        BulkStatus.FAILED,
                        "VALIDATION_ERROR",
                        joinViolationMsgs(cve),
                        null,
                        null
                ));
                failed++;

            } catch (UnexpectedRollbackException ure) {
                results.add(new BulkItemResultDto(
                        dto.productUrl(),
                        BulkStatus.FAILED,
                        "TRANSACTION_ROLLBACK",
                        "트랜잭션이 롤백되었습니다",
                        null,
                        null
                ));
                failed++;

            } catch (Exception e) {
                results.add(new BulkItemResultDto(
                        dto.productUrl(),
                        BulkStatus.FAILED,
                        "UNEXPECTED_ERROR",
                        "예상치 못한 오류가 발생했습니다",
                        null,
                        null
                ));
                failed++;
            }
        }

        return new CrawlingProductBulkSaveResponseDto(
                new BulkSummaryDto(requestDtoList.size(), success, duplicated, failed),
                results
        );
    }

    /*
     * 페이징 조회 + 동적 검색 (기본)
     */
    @Transactional(readOnly = true)
    public Page<CrawlingProductResponseDto> getProducts(
            String keyword,
            Integer minPrice,
            Integer maxPrice,
            String category,
            String platform,
            String sellerName,
            Gender gender,
            Age age,
            Boolean isConfirmed,
            Pageable pageable
    ) {
        Pageable safePageable = normalizeSort(pageable);

        KeywordNormalized kn = normalizeKeyword(keyword);

        Page<CrawlingProduct> page = crawlingProductRepository.search(
                kn.rawLower(), kn.noSpaceLower(),
                minPrice, maxPrice, category, platform, sellerName, gender, age, isConfirmed,
                safePageable
        );

        return page.map(CrawlingProductMapper::toDto);
    }

    /*
     * 페이징 조회 + 동적 검색 (벡터 스토어 적용)
     * - Vector Top-K 후보를 먼저 가져오고
     * - 그 후보에 대해 Java에서 2차 필터링을 적용한 뒤
     * - 메모리 페이징으로 반환
     */
    @Transactional(readOnly = true)
    public Page<CrawlingProductResponseDto> getProductsSimilaritySearch(
            String keyword,
            Integer minPrice,
            Integer maxPrice,
            String category,
            String platform,
            String sellerName,
            Gender gender,
            Age age,
            Boolean isConfirmed,
            Integer limit,
            Pageable pageable
    ) {
        Pageable safePageable = normalizeSort(pageable);

        int effectiveMinPrice = (minPrice != null) ? minPrice : 0;
        int effectiveMaxPrice = (maxPrice != null) ? maxPrice : Integer.MAX_VALUE;

        // keyword 없으면 기존 DB 검색
        KeywordNormalized kn = normalizeKeyword(keyword);
        if (!kn.hasKeyword()) {
            Page<CrawlingProduct> page = crawlingProductRepository.search(
                    null, null,
                    minPrice, maxPrice, category, platform, sellerName, gender, age, isConfirmed,
                    safePageable
            );
            return page.map(CrawlingProductMapper::toDto);
        }

        // 키워드당 최대 10개만 보여주기
        final int PER_KEYWORD_LIMIT = 10;

        VectorProductSearch vectorSearch = vectorProductSearchProvider.getIfAvailable();
        if (vectorSearch == null) {
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        String query = kn.rawLower();

        // limit이 들어오면 그걸 쓰고, 없으면 자동 계산 (기존 로직 유지)
        int defaultTopK = Math.max(safePageable.getPageSize() * 10, 200);
        int topK = sanitizeTopK(limit, defaultTopK);

        // 벡터 단계에서는 완화된 가격 범위로 후보 확보
        PriceRange vectorRange = relaxedRange(effectiveMinPrice, effectiveMaxPrice);

        List<VectorProductSearch.ScoredId> hits;
        try {
            // 1차: 완화된 가격 범위
            hits = vectorSearch.searchWithScores(
                    query,
                    vectorRange.min(),
                    vectorRange.max(),
                    null,
                    null,
                    topK,
                    null // threshold 없음
            );

            // 2차 보강: 너무 적으면 전체 범위로 후보 보강
            if (hits.size() < PER_KEYWORD_LIMIT) {
                List<VectorProductSearch.ScoredId> secondary = vectorSearch.searchWithScores(
                        query,
                        0,
                        Integer.MAX_VALUE,
                        null,
                        null,
                        topK,
                        null
                );

                Map<Long, Double> mergedScore = new LinkedHashMap<>();
                for (var h : hits) mergedScore.put(h.productId(), h.score());
                for (var h : secondary) mergedScore.putIfAbsent(h.productId(), h.score());

                hits = mergedScore.entrySet().stream()
                        .map(e -> new VectorProductSearch.ScoredId(e.getKey(), e.getValue()))
                        .toList();
            }

        } catch (Exception e) {
            log.warn("[VECTOR][SEARCH][ERROR] q='{}', cause={}", query, e.toString());
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        if (hits.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        // hitIds 조회
        List<Long> hitIds = hits.stream()
                .map(VectorProductSearch.ScoredId::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<CrawlingProduct> candidates =
                crawlingProductQueryRepository.searchByIdsWithKeywords(hitIds);

        // scoreMap 구성
        Map<Long, Double> scoreMap = hits.stream()
                .collect(Collectors.toMap(
                        VectorProductSearch.ScoredId::productId,
                        VectorProductSearch.ScoredId::score,
                        Double::max
                ));

        // score 높은 순 정렬
        candidates = candidates.stream()
                .sorted((a, b) -> Double.compare(
                        scoreMap.getOrDefault(b.getId(), 0.0),
                        scoreMap.getOrDefault(a.getId(), 0.0)
                ))
                .toList();

        // 2차 필터 + 키워드 포함 우선(기존 유지)
        List<CrawlingProduct> filtered = candidates.stream()
                .filter(p -> matchesFilters(
                        p,
                        effectiveMinPrice,
                        effectiveMaxPrice,
                        category,
                        platform,
                        sellerName,
                        gender,
                        age,
                        isConfirmed
                ))
                .sorted((a, b) -> {
                    boolean aMatch = containsKeyword(a, kn);
                    boolean bMatch = containsKeyword(b, kn);
                    if (aMatch == bMatch) return 0;
                    return aMatch ? -1 : 1;
                })
                .limit(PER_KEYWORD_LIMIT)
                .toList();

        int start = (int) safePageable.getOffset();
        int end = Math.min(start + safePageable.getPageSize(), filtered.size());

        List<CrawlingProductResponseDto> dtoList =
                (start >= end)
                        ? List.of()
                        : filtered.subList(start, end).stream()
                        .map(CrawlingProductMapper::toDto)
                        .toList();

        return new PageImpl<>(dtoList, safePageable, filtered.size());
    }

    /**
     * ✅ Java 2차 필터링
     * - topK 후보에 대해 조건을 최종 적용
     */
    private boolean matchesFilters(
            CrawlingProduct p,
            int minPrice,
            int maxPrice,
            String category,
            String platform,
            String sellerName,
            Gender gender,
            Age age,
            Boolean isConfirmed
    ) {
        // price
        Integer price = p.getPrice();
        if (price == null) return false;
        if (price < minPrice || price > maxPrice) return false;

        // category (완전일치/전방일치 정책은 취향대로 선택)
        if (category != null && !category.isBlank()) {
            String pc = safe(p.getCategory());
            if (pc == null) return false;

            // ✅ 예: "디지털>음향가전" 같은 계층형이면 startsWith가 더 현실적
            // 완전 일치가 필요하면 equals로 변경
            if (!pc.startsWith(category)) return false;
        }

        // platform
        if (platform != null && !platform.isBlank()) {
            String pp = safe(p.getPlatform());
            if (pp == null || !pp.equalsIgnoreCase(platform)) return false;
        }

        // sellerName
        if (sellerName != null && !sellerName.isBlank()) {
            String ps = safe(p.getSellerName());
            if (ps == null) return false;
            if (!ps.toLowerCase(Locale.ROOT).contains(sellerName.toLowerCase(Locale.ROOT))) return false;
        }

        // gender
        if (gender != null) {
            if (p.getGender() == null || p.getGender() != gender) return false;
        }

        // age
        if (age != null) {
            if (p.getAge() == null || p.getAge() != age) return false;
        }

        // isConfirmed
        if (isConfirmed != null) {
            if (p.getIsConfirmed() == null || !p.getIsConfirmed().equals(isConfirmed)) return false;
        }

        return true;
    }

    private int sanitizeTopK(Integer limit, int defaultTopK) {
        if (limit == null) return defaultTopK;
        // 너무 작으면 필터 후 비기 쉬움 / 너무 크면 비용 증가
        int v = limit;
        if (v < 50) v = 50;
        if (v > 2000) v = 2000;
        return v;
    }

    private record PriceRange(int min, int max) {}

    private PriceRange relaxedRange(int min, int max) {
        // max가 무한대면 굳이 완화하지 않음
        if (max == Integer.MAX_VALUE) {
            return new PriceRange(Math.max(0, min), Integer.MAX_VALUE);
        }
        int slack = 20_000; // 운영 튜닝 값
        int rMin = Math.max(0, min - slack);
        int rMax = Math.min(Integer.MAX_VALUE, max + slack);
        return new PriceRange(rMin, rMax);
    }

    /**
     * Null-safe string
     */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /*
     * 상품 상세 조회
     */
    @Transactional(readOnly = true)
    public CrawlingProductResponseDto getProduct(Long productId) {
        CrawlingProduct p = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        return new CrawlingProductResponseDto(
                p.getId(),
                p.getOriginalName(),
                p.getDisplayName(),
                p.getShortDescription(),
                p.getPrice(),
                p.getImageUrl(),
                p.getProductUrl(),
                p.getCategory(),
                p.getKeywords(),
                p.getReviewCount(),
                p.getRating(),
                p.getSellerName(),
                p.getPlatform(),
                p.getScore(),
                p.getAdminCheck(),
                p.getGender(),
                p.getAge(),
                p.getIsConfirmed(),
                p.getIsAdvertised(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    /*
     * 단건 부분 수정 (보낸 값만 적용)
     */
    @Transactional
    public CrawlingProductResponseDto updateProduct(Long productId, CrawlingProductUpdateRequestDto requestDto) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        boolean keywordsChanged = false;
        boolean textChanged = false;

        if (requestDto.displayName() != null) {
            String displayName = requestDto.displayName().trim();
            if (displayName.isBlank()) throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
            product.changeDisplayName(displayName);
            textChanged = true;
        }

        if (requestDto.price() != null) {
            if (requestDto.price() < 0) throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
            product.changePrice(requestDto.price());
        }

        if (requestDto.imageUrl() != null) product.changeImageUrl(requestDto.imageUrl().trim());
        if (requestDto.productUrl() != null) product.changeProductUrl(requestDto.productUrl().trim());

        if (requestDto.category() != null) {
            product.changeCategory(requestDto.category().trim());
            textChanged = true;
        }

        if (requestDto.keywords() != null) {
            List<String> normalized = normalizeKeywords(requestDto.keywords());
            product.changeKeywords(normalized);
            keywordsChanged = true;
        }

        if (requestDto.sellerName() != null) product.changeSellerName(requestDto.sellerName().trim());
        if (requestDto.platform() != null) product.changePlatform(requestDto.platform().trim());

        if (requestDto.gender() != null) {
            product.changeGender(parseGender(requestDto.gender()));
        }

        if (requestDto.age() != null) {
            product.changeAge(parseAge(requestDto.age()));
        }

        if (requestDto.isConfirmed() != null) {
            product.changeConfirmed(requestDto.isConfirmed());
        }

        validatePrice(product.getPrice());

        if (keywordsChanged || textChanged) {
            syncProductVectorSafely(product);
        }

        return CrawlingProductMapper.toDto(product);
    }

    /*
     * 점수 부여 + adminCheck true
     */
    @Transactional
    public ScoreResponseDto giveScore(Long productId, ScoreRequestDto requestDto) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        product.addScore(requestDto.score());
        product.changeAdminCheck(true);

        return new ScoreResponseDto(
                product.getId(),
                product.getScore(),
                product.getAdminCheck(),
                product.getIsConfirmed(),
                product.getUpdatedAt()
        );
    }

    /*
     * 컨펌 상태 변경
     */
    @Transactional
    public ConfirmResponseDto updateConfirmStatus(Long productId, ConfirmRequestDto requestDto) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        product.changeConfirmed(requestDto.isConfirmed());

        return new ConfirmResponseDto(
                product.getId(),
                product.getIsConfirmed(),
                product.getUpdatedAt()
        );
    }

    /*
     * 컨펌 상태 일괄 변경
     */
    @Transactional
    public ConfirmBulkResponseDto updateConfirmStatusBulk(ConfirmBulkRequestDto request) {
        List<Long> ids = request.ids();
        boolean toConfirm = Boolean.TRUE.equals(request.isConfirmed());

        int affected = crawlingProductRepository.bulkUpdateConfirm(ids, toConfirm);

        if (affected != ids.size()) {
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        return new ConfirmBulkResponseDto(affected, ids);
    }

    /*
     * 연령대 단건 변경
     */
    @Transactional
    public AgeResponseDto updateAge(AgeRequestDto request) {
        CrawlingProduct product = crawlingProductRepository.findById(request.id())
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        product.changeAge(request.age());

        return new AgeResponseDto(product.getId(), product.getAge());
    }

    /*
     * 연령대 일괄 변경
     */
    @Transactional
    public AgeBulkResponseDto updateAgeBulk(AgeBulkRequestDto request) {
        int affected = crawlingProductRepository.bulkUpdateAge(request.ids(), request.age());

        if (affected != request.ids().size()) {
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        return new AgeBulkResponseDto(affected, request.ids(), request.age());
    }

    /*
     * 성별 단건 변경
     */
    @Transactional
    public GenderResponseDto updateGender(GenderRequestDto request) {
        CrawlingProduct product = crawlingProductRepository.findById(request.id())
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        product.changeGender(request.gender());
        return new GenderResponseDto(product.getId(), product.getGender());
    }

    /*
     * 성별 일괄 변경
     */
    @Transactional
    public GenderBulkResponseDto updateGenderBulk(GenderBulkRequestDto request) {
        int affected = crawlingProductRepository.bulkUpdateGender(request.ids(), request.gender());

        if (affected != request.ids().size()) {
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        return new GenderBulkResponseDto(affected, request.ids(), request.gender());
    }

    /*
     * 키워드 일괄 저장 (기존 키워드는 유지하고, 입력한 키워드만 추가)
     */
    @Transactional
    public ProductKeywordBulkSaveResponse saveKeywordsBulk(ProductKeywordBulkSaveRequest request) {
        if (request == null
                || request.productIds() == null || request.productIds().isEmpty()
                || request.keywords() == null || request.keywords().isEmpty()) {
            throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
        }

        List<CrawlingProduct> products = crawlingProductRepository.findByIdIn(request.productIds());

        if (products.size() != request.productIds().size()) {
            Set<Long> requested = new HashSet<>(request.productIds());
            Set<Long> found = products.stream()
                    .map(CrawlingProduct::getId)
                    .collect(Collectors.toSet());
            requested.removeAll(found);

            log.warn("키워드 일괄 추가 중 일부 상품을 찾지 못했습니다. missingIds={}", requested);
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        List<String> normalizedNewKeywords = normalizeKeywords(request.keywords());

        for (CrawlingProduct product : products) {
            List<String> existing = product.getKeywords();
            if (existing == null) existing = new ArrayList<>();

            LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
            merged.addAll(normalizedNewKeywords);

            product.changeKeywords(new ArrayList<>(merged));
        }

        syncProductVectorSafely(products);

        List<Long> ids = products.stream()
                .map(CrawlingProduct::getId)
                .toList();

        log.info("CrawlingProduct 키워드 일괄 추가 완료. affected={}", ids.size());

        return new ProductKeywordBulkSaveResponse(ids.size(), ids, normalizedNewKeywords);
    }

    /*
     * 키워드 일괄 수정 (기존 키워드를 모두 덮어쓰기)
     */
    @Transactional
    public ProductKeywordBulkSaveResponse updateKeywordsBulk(ProductKeywordBulkUpdateRequest request) {
        if (request == null
                || request.productIds() == null || request.productIds().isEmpty()
                || request.keywords() == null || request.keywords().isEmpty()) {
            throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
        }

        List<CrawlingProduct> products = crawlingProductRepository.findByIdIn(request.productIds());

        if (products.size() != request.productIds().size()) {
            Set<Long> requested = new HashSet<>(request.productIds());
            Set<Long> found = products.stream()
                    .map(CrawlingProduct::getId)
                    .collect(Collectors.toSet());
            requested.removeAll(found);

            log.warn("키워드 일괄 수정 중 일부 상품을 찾지 못했습니다. missingIds={}", requested);
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        List<String> normalized = normalizeKeywords(request.keywords());

        for (CrawlingProduct product : products) {
            product.changeKeywords(new ArrayList<>(normalized));
        }

        syncProductVectorSafely(products);

        List<Long> ids = products.stream()
                .map(CrawlingProduct::getId)
                .toList();

        log.info("CrawlingProduct 키워드 일괄 수정 완료. affected={}", ids.size());

        return new ProductKeywordBulkSaveResponse(ids.size(), ids, normalized);
    }

    /*
     * 통계 보기
     */
    @Transactional(readOnly = true)
    public KeywordStatsResponse getStats(String keyword) {
        String word = (keyword == null) ? "" : keyword.trim();
        if (word.isEmpty()) {
            return new KeywordStatsResponse(0, Map.of(), Map.of(), Map.of());
        }

        List<CrawlingProduct> products = crawlingProductRepository.findByKeyword(word);

        int total = products.size();

        Map<String, Long> genderStats = products.stream()
                .collect(groupingBy(
                        p -> p.getGender() != null ? p.getGender().name() : "ANY",
                        counting()
                ));

        Map<String, Long> ageStats = products.stream()
                .collect(groupingBy(
                        p -> p.getAge() != null ? p.getAge().name() : "NONE",
                        counting()
                ));

        Map<String, Long> priceStats = products.stream()
                .collect(groupingBy(
                        p -> classifyPrice(p.getPrice()),
                        counting()
                ));

        return new KeywordStatsResponse(total, genderStats, ageStats, priceStats);
    }

    /*
     * 상품 삭제 (벡터 스토어에서도 상품 삭제)
     */
    @Transactional
    public void deleteProduct(Long productId) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(
                        ExceptionEnum.PRODUCT_NOT_FOUND
                ));

        crawlingProductRepository.delete(product);

        eventPublisher.publishEvent(new ProductDeletedEvent(product.getId()));
    }

    /**
     * 키워드 정규화:
     * - trim
     * - blank면 empty
     * - lower
     * - noSpace(lower + 모든 공백 제거)
     */
    private KeywordNormalized normalizeKeyword(String keyword) {
        if (keyword == null) return KeywordNormalized.empty();
        String trimmed = keyword.trim();
        if (trimmed.isBlank()) return KeywordNormalized.empty();

        String lower = trimmed.toLowerCase();
        String noSpace = lower.replaceAll("\\s+", "");
        return new KeywordNormalized(lower, noSpace);
    }

    /**
     * 검색 키워드가 상품의 keywords/제목/카테고리에 포함되어 있는지 여부
     * - 공백 제거 버전까지 같이 검사
     */
    private boolean containsKeyword(CrawlingProduct p, KeywordNormalized kn) {
        if (kn == null || !kn.hasKeyword()) return false;

        String q = kn.rawLower();
        String qNoSpace = kn.noSpaceLower();

        if (p.getKeywords() != null && !p.getKeywords().isEmpty()) {
            for (String kw : p.getKeywords()) {
                if (containsNormalized(kw, q, qNoSpace)) return true;
            }
        }

        if (containsNormalized(p.getDisplayName(), q, qNoSpace)) return true;
        if (containsNormalized(p.getCategory(), q, qNoSpace)) return true;
        if (containsNormalized(p.getOriginalName(), q, qNoSpace)) return true;

        return false;
    }

    private boolean containsNormalized(String target, String q, String qNoSpace) {
        if (target == null) return false;
        String t = target.toLowerCase();

        if (t.contains(q)) return true;

        String tNoSpace = t.replace(" ", "");
        return tNoSpace.contains(qNoSpace);
    }

    /**
     * 카테고리에서 메인 카테고리만 뽑아내는 헬퍼
     */
    private String extractMainCategory(String category) {
        if (category == null) return "";
        String[] parts = category.split(">");
        return parts[0].trim();
    }

    /**
     * 정규화된 키워드 값 객체
     */
    private record KeywordNormalized(String rawLower, String noSpaceLower) {
        static KeywordNormalized empty() {
            return new KeywordNormalized(null, null);
        }

        boolean hasKeyword() {
            return rawLower != null && !rawLower.isBlank();
        }
    }

    private String classifyPrice(Integer price) {
        if (price == null || price < 0) return "UNKNOWN";
        int p = price;

        if (p < 10_000) return "0-1만원";
        if (p < 30_000) return "1-3만원";
        if (p < 50_000) return "3-5만원";
        if (p < 100_000) return "5-10만원";
        if (p < 200_000) return "10-20만원";
        return "20만원 이상";
    }

    private String generateDisplayName(String originalName) {
        if (originalName == null) return null;

        String name = originalName;

        name = name.replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\{.*?\\}", "");

        name = name.replaceAll("[★♥●◆◎※]", "");

        String[] removeKeywords = {
                "무료배송", "빠른배송", "사은품", "당일발송",
                "세트", "세트상품", "1\\+1", "2\\+1", "3\\+1",
                "인기", "추천", "HOT", "Best", "BEST", "신상품"
        };
        for (String keyword : removeKeywords) {
            name = name.replaceAll("(?i)" + keyword, "");
        }

        name = name.trim().replaceAll("\\s{2,}", " ");
        return name;
    }

    private Pageable normalizeSort(Pageable pageable) {
        Sort input = pageable.getSort();
        Sort filtered = Sort.unsorted();

        if (input != null && input.isSorted()) {
            for (Sort.Order order : input) {
                String prop = order.getProperty();
                if (ProductSort.isAllowed(prop)) {
                    filtered = filtered.and(Sort.by(
                            order.isAscending() ? Sort.Order.asc(prop) : Sort.Order.desc(prop)
                    ));
                }
            }
        }

        if (filtered.isUnsorted()) {
            filtered = ProductSort.defaultSort();
        }

        boolean hasCreatedAt = filtered.stream().anyMatch(o -> o.getProperty().equals("createdAt"));
        if (!hasCreatedAt) {
            filtered = filtered.and(Sort.by(Sort.Order.desc("createdAt")));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), filtered);
    }

    private void validatePrice(Integer price) {
        if (price != null && price < 0) throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        if (root instanceof java.sql.SQLException se) {
            String sqlState = se.getSQLState();
            int vendorCode = se.getErrorCode();
            if ("23000".equals(sqlState) || vendorCode == 1062) return true;
        }
        String msg = root != null ? root.getMessage() : e.getMessage();
        return containsDuplicateKeyword(msg);
    }

    private boolean containsDuplicateKeyword(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("duplicate") || m.contains("unique") || m.contains("uq");
    }

    private String joinViolationMsgs(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
    }

    private void syncProductVectorSafely(CrawlingProduct product) {
        try {
            ProductVectorService vectorService = productVectorServiceProvider.getIfAvailable();
            if (vectorService == null) {
                log.info("[INFO] Vector feature disabled (vector.enabled=false), Qdrant sync will be skipped.");
                return;
            }

            if (product == null || product.getId() == null) return;

            String title = product.getDisplayName();
            if (title == null || title.isBlank()) title = product.getOriginalName();
            if (title == null || title.isBlank()) {
                log.warn("Qdrant 동기화 스킵 - title 없음. productId={}", product.getId());
                return;
            }

            long price = (product.getPrice() != null) ? product.getPrice().longValue() : 0L;

            vectorService.upsertProduct(
                    product.getId(),
                    title,
                    price,
                    product.getCategory(),
                    product.getShortDescription(),
                    product.getKeywords()
            );
        } catch (Exception e) {
            Long productId = (product == null) ? null : product.getId();
            log.warn("Qdrant 벡터 동기화 실패. productId={}, cause={}", productId, e.getMessage(), e);
        }
    }

    private void syncProductVectorSafely(List<CrawlingProduct> products) {
        if (products == null || products.isEmpty()) return;
        for (CrawlingProduct product : products) {
            syncProductVectorSafely(product);
        }
    }

    private List<String> normalizeKeywords(List<String> kws) {
        return kws.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.length() > 30 ? s.substring(0, 30) : s)
                .distinct()
                .toList();
    }

    private static Gender parseGender(String raw) {
        if (raw == null) return null;
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
        }
    }

    private static Age parseAge(String raw) {
        if (raw == null) return null;
        try {
            return Age.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
        }
    }
}
