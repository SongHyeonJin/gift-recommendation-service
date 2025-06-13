package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecommendationRequestDto(
        @Schema(description = "관계", example = "남자친구")
        String relation,
        @Schema(description = "최소 가격", example = "50000")
        int minPrice,
        @Schema(description = "최대 가격", example = "100000")
        int maxPrice,
        @Schema(description = "대표 키워드 목록", example = "[\"러닝화\", \"러닝가방\", \"바람막이\", \"스마트워치\"]")
        List<String> keywords
) {}
