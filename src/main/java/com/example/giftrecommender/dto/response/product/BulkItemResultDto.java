package com.example.giftrecommender.dto.response.product;

import com.example.giftrecommender.domain.enums.BulkStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "벌크 저장 시 개별 항목 처리 결과")
public record BulkItemResultDto(
        @Schema(description = "요청 상품 URL") String url,
        @Schema(description = "처리 상태") BulkStatus status,
        @Schema(description = "사유 코드 (예: DUPLICATE_KEY, VALIDATION_ERROR)") String reasonCode,
        @Schema(description = "사유 메시지 (사용자 친화)") String reasonMessage,
        @Schema(description = "저장된 상품 ID(성공 시만)") Long id,
        @Schema(description = "성공 시 저장된 상품 상세") CrawlingProductResponseDto data
) {}
