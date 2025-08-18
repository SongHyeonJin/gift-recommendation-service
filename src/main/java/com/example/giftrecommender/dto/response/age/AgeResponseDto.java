package com.example.giftrecommender.dto.response.age;

import com.example.giftrecommender.domain.enums.Age;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 연령대(Age) 단건 변경 응답")
public record AgeResponseDto(
        @Schema(description = "상품 ID", example = "123") Long id,
        @Schema(description = "변경된 연령대", example = "YOUNG_ADULT") Age age
) {}
