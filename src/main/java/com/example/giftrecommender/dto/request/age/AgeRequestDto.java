package com.example.giftrecommender.dto.request.age;

import com.example.giftrecommender.domain.enums.Age;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "상품 연령대(Age) 단건 변경 요청")
public record AgeRequestDto(
        @Schema(description = "상품 ID", example = "1")
        @NotNull Long id,

        @Schema(description = "변경할 연령대", example = "YOUNG_ADULT")
        @NotNull Age age
) {}
