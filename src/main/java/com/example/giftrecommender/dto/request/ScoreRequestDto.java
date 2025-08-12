package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ScoreRequestDto(
        @Schema(description = "관리자 수동 점수", example = "10")
        @NotNull @PositiveOrZero
        Integer score
) {}
