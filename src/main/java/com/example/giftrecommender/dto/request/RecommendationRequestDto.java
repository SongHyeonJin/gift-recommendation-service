package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecommendationRequestDto(
        @Schema(description = "대표 키워드 목록", example = "[\"연인\", \"5만원 이상 10만원 이하\", \"운동\", \"다이어트\"]")
        List<String> keywords
) {}
