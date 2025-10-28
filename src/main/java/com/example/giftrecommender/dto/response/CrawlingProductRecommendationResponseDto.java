package com.example.giftrecommender.dto.response;

import com.example.giftrecommender.dto.response.product.CrawlingProductResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "크롤링 상품 추천 결과 응답 DTO")
public record CrawlingProductRecommendationResponseDto(
        @Schema(description = "추천된 상품 목록")
        List<CrawlingProductResponseDto> products
) {}