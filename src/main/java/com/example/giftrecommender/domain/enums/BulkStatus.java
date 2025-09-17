package com.example.giftrecommender.domain.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "벌크 저장 시 각 항목 처리 상태")
public enum BulkStatus {
    @Schema(description = "정상 저장") SUCCESS,
    @Schema(description = "중복으로 저장 안됨") DUPLICATED,
    @Schema(description = "그 외 실패") FAILED
}
