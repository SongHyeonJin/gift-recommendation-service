package com.example.giftrecommender.dto.response.confirm;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ConfirmResponseDto(
        @Schema(description = "상품 ID") Long id,
        @Schema(description = "컨펌 여부") Boolean isConfirmed,
        @Schema(description = "수정 시각") LocalDateTime updatedAt
) {}
