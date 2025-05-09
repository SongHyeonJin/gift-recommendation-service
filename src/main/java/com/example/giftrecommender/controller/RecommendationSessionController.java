package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.response.RecommendationSessionResponseDto;
import com.example.giftrecommender.service.RecommendationSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "02-추천 세션", description = "추천 세션 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests/{guestId}/recommendation-sessions")
public class RecommendationSessionController {

    private final RecommendationSessionService recommendationSessionService;

    @Operation(
            summary = "추천 세션 생성",
            description = "게스트 ID를 기반으로 새로운 추천 세션을 생성합니다. (DB에 없을 시 생성한 게스트 ID를 넣어서 진행하시면 됩니다.)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 세션 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping
    public ResponseEntity<BasicResponseDto<RecommendationSessionResponseDto>> createRecommendationSession(
            @PathVariable("guestId") UUID guestId) {;
        return ResponseEntity.ok(BasicResponseDto.success("추천 세션 등록", recommendationSessionService.createRecommendationSession(guestId)));
    }

}
