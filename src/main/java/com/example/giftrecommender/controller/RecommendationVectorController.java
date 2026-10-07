package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.CrawlingProductRecommendationResponseDto;
import com.example.giftrecommender.service.RecommendationVectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecommendationVectorController {
    private final RecommendationVectorService recommendationVectorService;

    @Operation(summary = "선물 추천 요청", description = "대표 키워드와 가격 조건을 기반으로 상품을 추천받습니다. (시간당 호출 10회 제한)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 성공",
                    content = @Content(schema = @Schema(implementation = CrawlingProductRecommendationResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 또는 세션 없음")
    })
    @PostMapping("/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation/vector")
    public ResponseEntity<BasicResponseDto<CrawlingProductRecommendationResponseDto>> getRecommendationVector(
            @Parameter(
                    description = "게스트 ID",
                    example = "a31bbec5-1886-41c9-a079-9a375a6dfadb"
            )
            @PathVariable("guestId") UUID guestId,

            @Parameter(
                    description = "추천 세션 ID",
                    example = "9e3b4b95-7698-4feb-8c64-956c77e65bf8"
            )
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody @Valid RecommendationRequestDto request
    ) {
        CrawlingProductRecommendationResponseDto response =
                recommendationVectorService.recommendByVector(guestId, sessionId, request);
        return ResponseEntity.ok(BasicResponseDto.success("추천 완료", response));
    }

    @Operation(
            summary = "벡터 기반 상품 검색",
            description = """
                프론트에서 키워드별로 호출해 결과를 픽할 수 있도록,
                벡터 검색 결과를 2차 필터링(대분류 dominant + 슬롯 키워드 매칭) 후 반환합니다.
                """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공",
                    content = @Content(schema = @Schema(implementation = CrawlingProductRecommendationResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 또는 세션 없음")
    })
    @PostMapping("/search/vector")
    public ResponseEntity<BasicResponseDto<CrawlingProductRecommendationResponseDto>> searchVector(
            @Parameter(description = "이번 호출에서 대표로 검색할 슬롯 키워드(예: 주얼리/향수/디자이너 핸드백)")
            @RequestParam("keyword") String keyword,

            @Parameter(description = "최소 가격(원)", example = "10000")
            @RequestParam(name = "minPrice", required = false) Integer minPrice,

            @Parameter(description = "최대 가격(원)", example = "50000")
            @RequestParam(name = "maxPrice", required = false) Integer maxPrice,

            @Parameter(description = "최대 반환 개수", example = "20")
            @RequestParam(name = "limit", defaultValue = "20") int limit,

            @Parameter(description = "유사도 threshold (null이면 2차 필터로만 정제)", example = "0.78")
            @RequestParam(name = "threshold", required = false) Double threshold

    ) {
        CrawlingProductRecommendationResponseDto response =
                recommendationVectorService.searchByVector(
                        keyword,
                        minPrice,
                        maxPrice,
                        limit,
                        threshold
                );

        return ResponseEntity.ok(BasicResponseDto.success("검색 완료", response));
    }
}