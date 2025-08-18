package com.example.giftrecommender.dto.request.gender;

import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "상품 성별(Gender) 일괄 변경 요청")
public record GenderBulkRequestDto(
        @Schema(description = "변경할 상품 ID 목록", example = "[1,2]")
        @NotEmpty List<Long> ids,

        @Schema(description = "변경할 성별", example = "FEMALE")
        @NotNull Gender gender
) {}
