package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.enums.ProductSort;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.ConfirmBulkRequestDto;
import com.example.giftrecommender.dto.request.ConfirmRequestDto;
import com.example.giftrecommender.dto.request.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.request.ScoreRequestDto;
import com.example.giftrecommender.dto.response.ConfirmBulkResponseDto;
import com.example.giftrecommender.dto.response.ConfirmResponseDto;
import com.example.giftrecommender.dto.response.CrawlingProductResponseDto;
import com.example.giftrecommender.dto.response.ScoreResponseDto;
import com.example.giftrecommender.mapper.CrawlingProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrawlingProductService {

    private final CrawlingProductRepository crawlingProductRepository;

    /**
     * 단건 저장
     */
    @Transactional
    public CrawlingProductResponseDto save(CrawlingProductRequestDto requestDto) {
        int score = calculateScore(requestDto.rating(), requestDto.reviewCount());

        CrawlingProduct product = CrawlingProduct.builder()
                .originalName(requestDto.originalName())
                .displayName(generateDisplayName(requestDto.originalName())) // 노출용 이름 생성
                .price(requestDto.price())
                .imageUrl(requestDto.imageUrl())
                .productUrl(requestDto.productUrl())
                .category(requestDto.category())
                .keywords(requestDto.keywords())
                .reviewCount(requestDto.reviewCount())
                .rating(requestDto.rating())
                .score(score)
                .sellerName(requestDto.sellerName())
                .platform(requestDto.platform())
                .build();

        CrawlingProduct savedProduct = crawlingProductRepository.save(product);
        return CrawlingProductMapper.toDto(savedProduct);
    }

    /**
     * 여러건 저장
     */
    @Transactional
    public List<CrawlingProductResponseDto> saveAll(List<CrawlingProductRequestDto> requestDtoList) {
        List<CrawlingProduct> products = requestDtoList.stream()
                .map(dto -> {
                    int score = calculateScore(dto.rating(), dto.reviewCount());
                    return CrawlingProduct.builder()
                            .originalName(dto.originalName())
                            .displayName(generateDisplayName(dto.originalName()))
                            .price(dto.price())
                            .imageUrl(dto.imageUrl())
                            .productUrl(dto.productUrl())
                            .category(dto.category())
                            .keywords(dto.keywords())
                            .reviewCount(dto.reviewCount())
                            .rating(dto.rating())
                            .sellerName(dto.sellerName())
                            .platform(dto.platform())
                            .score(score)
                            .build();
                })
                .toList();

        List<CrawlingProduct> savedList = crawlingProductRepository.saveAll(products);
        return savedList.stream()
                .map(CrawlingProductMapper::toDto)
                .toList();
    }

    /**
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

    private int calculateScore(BigDecimal rating, Integer reviewCount) {
        int score = 0;
        if (rating != null && rating.compareTo(BigDecimal.valueOf(4.2)) >= 0) {
            score += 1;
        }
        if (reviewCount != null && reviewCount >= 100) {
            score += 1;
        }
        if (reviewCount != null && reviewCount >= 1000 && rating != null && rating.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            score += 1;
        }
        if (reviewCount != null && reviewCount >= 10000 && rating != null && rating.compareTo(BigDecimal.valueOf(4.3)) >= 0) {
            score += 1;
        }
        return score;
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
                "세트", "세트상품", "1+1", "2+1", "3+1",
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
}
