package com.example.giftrecommender.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RecommendationSessionResponseDto (
        @Schema(description = "생성된 추천 세션 ID", example = "2f90aa9a-5d10-46b0-a571-3e091354a4d6")
        UUID recommendationSessionId
) {}
