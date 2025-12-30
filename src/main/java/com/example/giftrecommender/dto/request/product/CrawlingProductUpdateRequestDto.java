package com.example.giftrecommender.dto.request.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "상품 단건 부분 수정 요청")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CrawlingProductUpdateRequestDto(
        @Schema(description = "노출명(관리자 수정용)", example = "무아스 마카롱 USB 큐브 멀티탭")
        @Size(max = 300)
        String displayName,

        @Schema(description = "가격(원)", example = "17900")
        @Min(0) Integer price,

        @Schema(description = "이미지 URL")
        @Size(max = 1000) String imageUrl,

        @Schema(description = "상품 상세 URL")
        @Size(max = 1000) String productUrl,

        @Schema(description = "카테고리", example = "디지털/PC")
        @Size(max = 100) String category,

        @Schema(description = "키워드", example = "[\"USB\",\"멀티탭\",\"케이블\",\"블루\"]")
        List<@NotBlank @Size(max = 30) String> keywords,

        @Schema(description = "판매자명", example = "무무샵")
        @Size(max = 100) String sellerName,

        @Schema(description = "플랫폼", example = "텐바이텐")
        @Size(max = 50) String platform,

        @Schema(description = "성별", example = "any", allowableValues = {"male","female","any"})
        String gender,

        @Schema(description = "연령대", example = "young_adult",
                allowableValues = {"kid","teen","young_adult","senior","none"})
        String age,

        @Schema(description = "컨펌 여부", example = "true")
        Boolean isConfirmed
) {}
