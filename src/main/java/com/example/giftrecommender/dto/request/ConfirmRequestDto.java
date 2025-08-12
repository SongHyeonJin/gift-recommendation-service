package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ConfirmRequestDto(
        @Schema(description = "컨펌 여부", example = "true")
        @NotNull
        Boolean isConfirmed
) {}