package com.example.giftrecommender.dto.response.age;


import com.example.giftrecommender.domain.enums.Age;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "상품 연령대(Age) 일괄 변경 응답")
public record AgeBulkResponseDto(
        @Schema(description = "실제 변경된 개수", example = "3") int affected,
        @Schema(description = "변경 대상 상품 ID 목록", example = "[1,2,3]") List<Long> ids,
        @Schema(description = "변경된 연령대", example = "TEEN") Age age
) {}
