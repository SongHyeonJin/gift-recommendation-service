package com.example.giftrecommender.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ScoreResponseDto(
        @Schema(description = "상품 ID") Long id,
        @Schema(description = "최종 점수") Integer score,
        @Schema(description = "관리자 점수 여부") Boolean adminCheck,
        @Schema(description = "컨펌 여부") Boolean isConfirmed,
        @Schema(description = "수정 시각") LocalDateTime updatedAt
) {}
