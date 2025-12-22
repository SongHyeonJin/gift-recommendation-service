package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.product.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.response.product.CrawlingProductResponseDto;
import com.example.giftrecommender.mapper.CrawlingProductMapper;
import com.example.giftrecommender.util.RecommendationUtil;
import com.example.giftrecommender.vector.ProductVectorService;
import com.example.giftrecommender.vector.event.ProductCreatedEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlingProductSaver {

    private final CrawlingProductRepository crawlingProductRepository;
    private final Validator validator;
    private final ApplicationEventPublisher publisher;
    private final ObjectProvider<ProductVectorService> productVectorServiceProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrawlingProductResponseDto save(CrawlingProductRequestDto requestDto) {
        Set<ConstraintViolation<CrawlingProductRequestDto>> v = validator.validate(requestDto);
        if (!v.isEmpty()) {
            throw new ConstraintViolationException(v);
        }

        // 점수 계산
        int score = RecommendationUtil.calculateScore(requestDto.rating(), requestDto.reviewCount());

        CrawlingProduct product = CrawlingProduct.builder()
                .originalName(requestDto.originalName())
                .displayName(requestDto.displayName() == null ?
                        RecommendationUtil.generateDisplayName(requestDto.originalName())
                        : RecommendationUtil.generateDisplayName(requestDto.displayName()))
                .shortDescription(requestDto.shortDescription() == null ? null : requestDto.shortDescription().trim()) // ✅ 추가
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
                .gender(requestDto.gender())
                .age(requestDto.age())
                .isAdvertised(Boolean.TRUE.equals(requestDto.isAdvertised()))
                .build();

        CrawlingProduct savedProduct = crawlingProductRepository.save(product);

        // 임베딩 정보 마킹
        Long pointId = savedProduct.getId();
        String model = "text-embedding-3-small";
        savedProduct.markEmbedding(pointId, model, false);

        syncProductVectorSafely(savedProduct, requestDto.keywords());

        publisher.publishEvent(new ProductCreatedEvent(
                savedProduct.getId(),
                savedProduct.getDisplayName(),
                savedProduct.getPrice(),
                savedProduct.getCategory(),
                savedProduct.getShortDescription()
        ));

        return CrawlingProductMapper.toDto(savedProduct);
    }

    /**
     * 저장 시점에서 바로 Qdrant 벡터 스토어에 업서트
     * - ProductVectorService가 비활성(vector.enabled=false)이면 그냥 스킵
     * - 예외가 터져도 상품 저장 자체는 실패하지 않도록 try-catch
     */
    private void syncProductVectorSafely(CrawlingProduct product, List<String> rawKeywords) {
        try {
            ProductVectorService vectorService = productVectorServiceProvider.getIfAvailable();
            if (vectorService == null) {
                log.info("[VECTOR] disabled (vector.enabled=false). skip sync. id={}", product.getId());
                return;
            }

            if (product == null || product.getId() == null) return;

            String title = product.getDisplayName();
            if (title == null || title.isBlank()) {
                title = product.getOriginalName();
            }
            if (title == null || title.isBlank()) {
                log.warn("[VECTOR] skip sync - no title. id={}", product.getId());
                return;
            }

            long price = product.getPrice() != null ? product.getPrice().longValue() : 0L;

            String category = product.getCategory();
            String shortDescription = product.getShortDescription();

            List<String> keywords = (rawKeywords == null) ? List.of() : rawKeywords;

            vectorService.upsertProduct(
                    product.getId(),
                    title,
                    price,
                    category,
                    shortDescription,
                    keywords
            );

            log.info("[VECTOR] direct sync ok. id={}, keywords.size={}", product.getId(), keywords.size());

        } catch (Exception e) {
            log.warn("[VECTOR] direct sync failed. id={}, cause={}", product.getId(), e.toString(), e);
        }
    }

}
