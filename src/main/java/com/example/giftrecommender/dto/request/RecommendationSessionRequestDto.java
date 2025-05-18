package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecommendationSessionRequestDto(
        @Schema(description = "추천 대상 이름", example = "회원1")
        String name
) {}
