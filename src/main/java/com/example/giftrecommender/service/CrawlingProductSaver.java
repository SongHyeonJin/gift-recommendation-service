package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.product.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.response.CrawlingProductResponseDto;
import com.example.giftrecommender.mapper.CrawlingProductMapper;
import com.example.giftrecommender.util.RecommendationUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrawlingProductSaver {

    private final CrawlingProductRepository crawlingProductRepository;
    private final Validator validator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrawlingProductResponseDto save(CrawlingProductRequestDto requestDto) {
        Set<ConstraintViolation<CrawlingProductRequestDto>> v = validator.validate(requestDto);
        if (!v.isEmpty()) {
            throw new ConstraintViolationException(v);
        }

        int score = RecommendationUtil.calculateScore(requestDto.rating(), requestDto.reviewCount());

        CrawlingProduct product = CrawlingProduct.builder()
                .originalName(requestDto.originalName())
                .displayName(RecommendationUtil.generateDisplayName(requestDto.originalName())) // 노출용 이름 생성
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

}
