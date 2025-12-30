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
            Pageable pageable
    ) {
        Pageable safePageable = normalizeSort(pageable);

        KeywordNormalized kn = normalizeKeyword(keyword);

        int effectiveMinPrice = (minPrice != null) ? minPrice : 0;
        int effectiveMaxPrice = (maxPrice != null) ? maxPrice : Integer.MAX_VALUE;

        // 검색어가 없다면 기존 검색만 사용
        if (!kn.hasKeyword()) {
            Page<CrawlingProduct> page = crawlingProductRepository.search(
                    null, null,
                    minPrice, maxPrice, category, platform, sellerName, gender, age, isConfirmed,
                    safePageable
            );
            return page.map(CrawlingProductMapper::toDto);
        }

        VectorProductSearch vectorSearch = vectorProductSearchProvider.getIfAvailable();

        String query = kn.rawLower();
        String queryNoSpace = kn.noSpaceLower();

        // (권장) lexical을 무한정 끌고 오지 않게 제한
        // 최소한 현 페이지 크기만큼 + buffer 정도만 확보
        int lexicalLimit = Math.max(safePageable.getPageSize() * 3, safePageable.getPageSize());

        // 1) 문자열 기반 매칭 상품 먼저 가져오기
        //    - 여기서 keywords 컬렉션 + 공백 제거 매칭까지 되도록 QueryRepository 쿼리를 수정해야 함
        List<CrawlingProduct> lexicalMatches =
                crawlingProductQueryRepository.searchByKeywordOrNameOrCategory(
                        query,
                        queryNoSpace,
                        effectiveMinPrice,
                        effectiveMaxPrice,
                        category,
                        platform,
                        sellerName,
                        gender,
                        age,
                        isConfirmed,
                        lexicalLimit
                );

        Set<Long> usedIds = lexicalMatches.stream()
                .map(CrawlingProduct::getId)
                .collect(Collectors.toSet());

        // 2) 벡터 검색 수행
        List<VectorProductSearch.ScoredId> hits = List.of();
        boolean useVector = (vectorSearch != null);

        if (useVector) {
            try {
                hits = vectorSearch.searchWithScores(
                        query,
                        effectiveMinPrice,
                        effectiveMaxPrice,
                        (age != null) ? age.name() : null,
                        (gender != null) ? gender.name() : null,
                        SIMILARITY_CANDIDATE_LIMIT,
                        SIMILARITY_THRESHOLD
                );
            } catch (Exception e) {
                log.warn("[VECTOR][SEARCH][ERROR] q='{}', cause={}", query, e.toString());
                hits = List.of();
            }
        }

        // 3) 벡터 검색된 상품을 DB에서 조회
        List<CrawlingProduct> vectorProducts = List.of();

        if (!hits.isEmpty()) {
            List<Long> hitIds = hits.stream()
                    .map(VectorProductSearch.ScoredId::productId)
                    .filter(id -> !usedIds.contains(id))
                    .toList();

            if (!hitIds.isEmpty()) {
                vectorProducts = crawlingProductQueryRepository.searchByIdsAndConditions(
                        hitIds,
                        null,
                        minPrice,
                        maxPrice,
                        category,
                        platform,
                        sellerName,
                        gender,
                        age,
                        isConfirmed
                );

                Map<Long, Double> scoreMap = hits.stream()
                        .collect(Collectors.toMap(
                                VectorProductSearch.ScoredId::productId,
                                VectorProductSearch.ScoredId::score,
                                Double::max
                        ));

                vectorProducts = vectorProducts.stream()
                        .sorted((a, b) -> Double.compare(
                                scoreMap.getOrDefault(b.getId(), 0.0),
                                scoreMap.getOrDefault(a.getId(), 0.0)
                        ))
                        .collect(Collectors.toList());
            }
        }

        // 4) 문자열 매칭 상품의 메인 카테고리 기준으로 벡터 결과 필터링 (lexical이 충분할 때만)
        Set<String> lexicalMainCategories = lexicalMatches.stream()
                .map(CrawlingProduct::getCategory)
                .filter(Objects::nonNull)
                .map(this::extractMainCategory)
                .collect(Collectors.toSet());

        if (!lexicalMainCategories.isEmpty() && lexicalMatches.size() >= 3) {
            vectorProducts = vectorProducts.stream()
                    .filter(p -> {
                        String cat = p.getCategory();
                        if (cat == null) return false;
                        String main = extractMainCategory(cat);
                        return lexicalMainCategories.contains(main);
                    })
                    .collect(Collectors.toList());
        }

        // 5) 벡터로 추가하는 개수 제한
        int maxVectorAdd = safePageable.getPageSize();
        if (vectorProducts.size() > maxVectorAdd) {
            vectorProducts = vectorProducts.subList(0, maxVectorAdd);
        }

        // 6) 문자열 매칭 + 벡터 매칭 합치기 (중복 제거)
        List<CrawlingProduct> merged = new ArrayList<>(lexicalMatches.size() + vectorProducts.size());
        merged.addAll(lexicalMatches);
        merged.addAll(vectorProducts);

        Map<Long, CrawlingProduct> uniqueMap = new LinkedHashMap<>();
        for (CrawlingProduct p : merged) {
            uniqueMap.put(p.getId(), p);
        }
        List<CrawlingProduct> resultList = new ArrayList<>(uniqueMap.values());

        // 7) 검색어가 keywords/제목/카테고리에 포함된 상품은 항상 상위로 (공백 제거 포함)
        resultList = resultList.stream()
                .sorted((a, b) -> {
                    boolean aMatch = containsKeyword(a, kn);
                    boolean bMatch = containsKeyword(b, kn);
                    if (aMatch == bMatch) return 0;
                    return aMatch ? -1 : 1;
                })
                .toList();

        // 8) pageable 적용 (메모리 페이징)
        int start = (int) safePageable.getOffset();
        int end = Math.min(start + safePageable.getPageSize(), resultList.size());

        List<CrawlingProductResponseDto> dtoList =
                (start >= end)
                        ? List.of()
                        : resultList.subList(start, end).stream()
                        .map(CrawlingProductMapper::toDto)
                        .toList();

        return new PageImpl<>(dtoList, safePageable, resultList.size());
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

    // =========================
    // 아래부터 Helper Methods
    // =========================

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
