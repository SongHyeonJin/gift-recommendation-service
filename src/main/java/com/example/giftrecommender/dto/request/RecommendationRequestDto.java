package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecommendationRequestDto(
        @Schema(description = "대표 키워드 목록", example = "[\"여자친구\", \"5~10만원\", \"생일\", \"악세서리\", \"우아한\"]")
        List<String> keywords
) {}
