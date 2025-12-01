package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.BulkStatus;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.enums.ProductSort;
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
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingProductService {

    private final CrawlingProductRepository crawlingProductRepository;
    private final CrawlingProductSaver crawlingProductSaver;
    private final ObjectProvider<ProductVectorService> productVectorServiceProvider;

    /*
     * 여러건 저장
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CrawlingProductBulkSaveResponseDto saveAll(List<CrawlingProductRequestDto> requestDtoList) {
        int success = 0, duplicated = 0, failed = 0;
        List<BulkItemResultDto> results = new ArrayList<>();

        for (CrawlingProductRequestDto dto : requestDtoList) {
            try {
                // 건별 신규 트랜잭션 (@Transactional(REQUIRES_NEW))인 다른 빈 호출
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
                    // 중복: 내부 원문 노출 금지 → 표준 코드/메시지로 치환
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
                    // 기타 트랜잭션 포장 예외
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
                // 마지막 방어: 내부 상세는 로그로만
                // log.error("Bulk save item failed. url={}, cause={}", dto.productUrl(), rootMessage(e));
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
     * 페이징 조회 + 동적 검색
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

        Page<CrawlingProduct> page = crawlingProductRepository.search(
                keyword, minPrice, maxPrice, category, platform, sellerName, gender, age, isConfirmed, safePageable
        );

        return page.map(CrawlingProductMapper::toDto);
    }

    /*
     *  상품 상세 조회
     */
    @Transactional(readOnly = true)
    public CrawlingProductResponseDto getProduct(Long productId) {
        CrawlingProduct p = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        return new CrawlingProductResponseDto(
                p.getId(),
                p.getOriginalName(),
                p.getDisplayName(),
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

        // displayName / originalName
        if (requestDto.originalName() != null) {
            String originalName = requestDto.originalName();

            product.changeOriginalName(originalName);
            product.changeDisplayName(generateDisplayName(originalName));
            textChanged = true;
        }

        // price
        if (requestDto.price() != null) {
            if (requestDto.price() < 0) throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
            product.changePrice(requestDto.price());
        }

        // imageUrl / productUrl
        if (requestDto.imageUrl() != null) product.changeImageUrl(requestDto.imageUrl().trim());
        if (requestDto.productUrl() != null) product.changeProductUrl(requestDto.productUrl().trim());

        // category
        if (requestDto.category() != null) {
            product.changeCategory(requestDto.category().trim());
            textChanged = true;
        }

        // keywords
        if (requestDto.keywords() != null) {
            List<String> normalized = normalizeKeywords(requestDto.keywords());
            product.changeKeywords(normalized);
            keywordsChanged = true;
        }

        // sellerName / platform
        if (requestDto.sellerName() != null) product.changeSellerName(requestDto.sellerName().trim());
        if (requestDto.platform() != null) product.changePlatform(requestDto.platform().trim());

        // gender
        if (requestDto.gender() != null) {
            product.changeGender(parseGender(requestDto.gender()));
        }

        // age
        if (requestDto.age() != null) {
            product.changeAge(parseAge(requestDto.age()));
        }

        // isConfirmed
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

        // 요청 개수와 실제 조회 개수가 다르면 에러
        if (products.size() != request.productIds().size()) {
            Set<Long> requested = new HashSet<>(request.productIds());
            Set<Long> found = products.stream()
                    .map(CrawlingProduct::getId)
                    .collect(Collectors.toSet());
            requested.removeAll(found); // 존재하지 않는 ID들

            log.warn("키워드 일괄 추가 중 일부 상품을 찾지 못했습니다. missingIds={}", requested);
            throw new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND);
        }

        List<String> normalizedNewKeywords = normalizeKeywords(request.keywords());

        for (CrawlingProduct product : products) {
            List<String> existing = product.getKeywords();
            if (existing == null) {
                existing = new ArrayList<>();
            }

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

    private String generateDisplayName(String originalName) {
        if (originalName == null) return null;

        String name = originalName;

        // 대괄호, 소괄호, 중괄호 안 내용 제거
        name = name.replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\{.*?\\}", "");

        // 특수문자/장식 기호 제거
        name = name.replaceAll("[★♥●◆◎※]", "");

        // 불필요한 키워드 제거
        String[] removeKeywords = {
                "무료배송", "빠른배송", "사은품", "당일발송",
                "세트", "세트상품", "1\\+1", "2\\+1", "3\\+1",
                "인기", "추천", "HOT", "Best", "BEST", "신상품"
        };
        for (String keyword : removeKeywords) {
            name = name.replaceAll("(?i)" + keyword, ""); // 대소문자 무시
        }

        // 앞뒤 공백 및 중복 공백 제거
        name = name.trim().replaceAll("\\s{2,}", " ");

        return name;
    }

    /*
     *   허용된 정렬만 통과 + 비어있으면 기본값(createdAt desc)
     */
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
            String sqlState = se.getSQLState(); // MySQL: 23000
            int vendorCode = se.getErrorCode(); // MySQL: 1062
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

    /*
     * Qdrant 벡터 스토어 동기화 (단건) - 실패해도 메인 로직은 터지지 않게 방어
     */
    private void syncProductVectorSafely(CrawlingProduct product) {
        try {
            ProductVectorService vectorService = productVectorServiceProvider.getIfAvailable();
            if (vectorService == null) {
                log.info("[INFO] Vector feature disabled (vector.enabled=false), Qdrant sync will be skipped.");
                return;
            }

            if (product == null || product.getId() == null) return;

            String title = product.getDisplayName();
            if (title == null || title.isBlank()) {
                title = product.getOriginalName();
            }
            if (title == null || title.isBlank()) {
                log.warn("Qdrant 동기화 스킵 - title 없음. productId={}", product.getId());
                return;
            }

            long price = product.getPrice() != null ? product.getPrice().longValue() : 0L;
            List<String> keywords = product.getKeywords();

            vectorService.upsertProduct(
                    product.getId(),
                    title,
                    price,
                    keywords
            );
        } catch (Exception e) {
            log.warn("Qdrant 벡터 동기화 실패. productId={}, cause={}",
                    product.getId(), e.getMessage(), e);
        }
    }

    /*
     * Qdrant 벡터 스토어 동기화 (다건)
     */
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
