package com.example.giftrecommender.dto.response.gender;

import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "상품 성별(Gender) 일괄 변경 응답")
public record GenderBulkResponseDto(
        @Schema(description = "실제 변경된 개수", example = "3") int affected,
        @Schema(description = "변경 대상 상품 ID 목록", example = "[1,2,3]") List<Long> ids,
        @Schema(description = "변경된 성별", example = "FEMALE") Gender gender
) {}