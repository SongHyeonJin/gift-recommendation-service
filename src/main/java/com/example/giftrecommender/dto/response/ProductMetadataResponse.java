package com.example.giftrecommender.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
public record ProductMetadataResponse(
        @Schema(description = "상품 ID", example = "123")
        Long productId,

        @Schema(description = "계층형 카테고리 경로", example = "패션의류>남성의류>재킷")
        String category,

        @Schema(description = "짧은 설명", example = "분무기 용도로 사용하기 좋은 가성비 제품")
        String shortDescription,

        @Schema(description = "키워드 목록")
        List<String> keywords,

        @Schema(description = "Qdrant 벡터 업데이트 성공 여부", example = "true")
        boolean vectorUpdated
) {}
