package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.product.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.response.CrawlingProductResponseDto;
import com.example.giftrecommender.mapper.CrawlingProductMapper;
import com.example.giftrecommender.util.RecommendationUtil;
import com.example.giftrecommender.vector.event.ProductCreatedEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrawlingProductSaver {

    private final CrawlingProductRepository crawlingProductRepository;
    private final Validator validator;
    private final ApplicationEventPublisher publisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrawlingProductResponseDto save(CrawlingProductRequestDto requestDto) {
        // 유효성 검증
        Set<ConstraintViolation<CrawlingProductRequestDto>> v = validator.validate(requestDto);
        if (!v.isEmpty()) {
            throw new ConstraintViolationException(v);
        }

        // 점수 계산
        int score = RecommendationUtil.calculateScore(requestDto.rating(), requestDto.reviewCount());

        // 엔티티 생성
        CrawlingProduct product = CrawlingProduct.builder()
                .originalName(requestDto.originalName())
                .displayName(requestDto.displayName() == null ?
                        RecommendationUtil.generateDisplayName(requestDto.originalName())
                        : RecommendationUtil.generateDisplayName(requestDto.displayName()))
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
                .build();

        // 저장
        CrawlingProduct savedProduct = crawlingProductRepository.save(product);

        // 임베딩 정보 마킹
        String pointId = String.valueOf(savedProduct.getId());
        String model   = "text-embedding-3-small";
        savedProduct.markEmbedding(pointId, model, false);

        // 이벤트 발행 → AFTER_COMMIT에서 임베딩/업서트 수행
        publisher.publishEvent(new ProductCreatedEvent(
                savedProduct.getId(),
                savedProduct.getDisplayName(),
                savedProduct.getPrice()
        ));

        return CrawlingProductMapper.toDto(savedProduct);
    }

}
