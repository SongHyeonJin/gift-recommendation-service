package com.example.giftrecommender.dto.request;

import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecommendationRequestDto(
        @Schema(description = "관계", example = "연인")
        String relation,
        @Schema(description = "나이", example = "20대")
        String age,
        @Schema(description = "성별", example = "FEMALE")
        Gender gender,
        @Schema(description = "최소 가격", example = "50000")
        int minPrice,
        @Schema(description = "최대 가격", example = "100000")
        int maxPrice,
        @Schema(description = "선물 이유", example = "기념일")
        String reason,
        @Schema(description = "취향", example = "잘 모르겠어")
        String preference,
        @Schema(description = "대표 키워드 목록", example = "[\"주얼리\", \"향수\", \"디자이너 핸드백\", \"명품 화장품 세트\", \"스파 체험권\"]")
        List<String> keywords
) {}
