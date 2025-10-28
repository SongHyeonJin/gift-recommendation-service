package com.example.giftrecommender.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추천 결과 응답 DTO")
public record RecommendationResponseDto<T>(
        @Schema(description = "추천된 상품 목록")
        List<T> products
) {
    public static <T> RecommendationResponseDto<T> of(List<T> products) {
        return new RecommendationResponseDto<>(products);
    }
}
