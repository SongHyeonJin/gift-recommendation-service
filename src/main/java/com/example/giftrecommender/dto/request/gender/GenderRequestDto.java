package com.example.giftrecommender.dto.request.gender;

import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "상품 성별(Gender) 단건 변경 요청")
public record GenderRequestDto(
        @Schema(description = "상품 ID", example = "1")
        @NotNull Long id,

        @Schema(description = "변경할 성별", example = "MALE")
        @NotNull Gender gender
) {}
