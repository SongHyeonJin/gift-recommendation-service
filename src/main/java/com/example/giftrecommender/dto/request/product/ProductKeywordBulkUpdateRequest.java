package com.example.giftrecommender.dto.request.product;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "여러 상품 키워드를 덮어쓰기(수정)하기 위한 요청 DTO")
public record ProductKeywordBulkUpdateRequest(

        @Schema(description = "수정할 상품 ID 목록", example = "[1, 2, 3]")
        List<Long> productIds,

        @Schema(description = "새로 설정할 키워드 목록", example = "[\"헬스\", \"운동 용품\", \"스포츠 용품\"]")
        List<String> keywords
) {}
