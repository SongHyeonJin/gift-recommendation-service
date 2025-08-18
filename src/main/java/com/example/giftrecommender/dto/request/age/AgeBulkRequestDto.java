package com.example.giftrecommender.dto.request.age;

import com.example.giftrecommender.domain.enums.Age;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "상품 연령대(Age) 일괄 변경 요청")
public record AgeBulkRequestDto(
        @Schema(description = "변경할 상품 ID 목록", example = "[1,2]")
        @NotEmpty List<Long> ids,

        @Schema(description = "변경할 연령대", example = "TEEN")
        @NotNull Age age
) {}
