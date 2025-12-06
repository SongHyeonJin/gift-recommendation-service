package com.example.giftrecommender.dto.request.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "여러 상품에 공통 키워드를 일괄 저장하기 위한 요청 DTO")
public record ProductKeywordBulkSaveRequest(

        @Schema(
                description = "키워드를 적용할 상품 ID 목록",
                example = "[1, 2, 3]"
        )
        List<Long> productIds,

        @Schema(
                description = "저장할 키워드 목록 (공통으로 적용)",
                example = "[\"운동화\", \"러닝\", \"데일리\"]"
        )
        List<String> keywords
) {}
