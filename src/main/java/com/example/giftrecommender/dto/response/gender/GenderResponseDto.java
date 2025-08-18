package com.example.giftrecommender.dto.response.gender;

import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 성별(Gender) 단건 변경 응답")
public record GenderResponseDto(
        @Schema(description = "상품 ID", example = "123") Long id,
        @Schema(description = "변경된 성별", example = "MALE") Gender gender
) {}
