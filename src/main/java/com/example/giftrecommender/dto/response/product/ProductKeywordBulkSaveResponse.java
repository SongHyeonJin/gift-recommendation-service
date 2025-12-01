package com.example.giftrecommender.dto.response.product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "여러 상품 키워드 일괄 저장 결과 DTO")
public record ProductKeywordBulkSaveResponse(

        @Schema(
                description = "키워드가 성공적으로 적용된 상품 개수",
                example = "3"
        )
        int affected,
        @Schema(
                description = "키워드가 적용된 상품 ID 목록",
                example = "[1, 2, 3]"
        )
        List<Long> ids,

        @Schema(
                description = "모든 상품에 공통으로 적용된 키워드 목록"
        )
        List<String> keywords
) {}
