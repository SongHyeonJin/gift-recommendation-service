package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.UserAnswerRequestDto;
import com.example.giftrecommender.service.UserAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "03-유저 응답 API (고정 질문, 선택지)", description = "추천 질문에 대한 유저 응답 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests/{guestId}/recommendation-sessions/{sessionId}")
public class UserAnswerController {

    private final UserAnswerService userAnswerService;

    @Operation(summary = "유저 응답 저장",
            description = "고정 질문(1~3번)에 대해 유저가 선택한 답변을 저장합니다. (questionId 1은 대상자 누구?, answerOptionId 1은 연인)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 저장 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식"),
            @ApiResponse(responseCode = "404", description = "세션 또는 질문, 선택지 정보 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/answers")
    public ResponseEntity<BasicResponseDto<Void>> saveAnswer(
            @PathVariable("guestId") UUID guestId,
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody UserAnswerRequestDto requestDto) {
        userAnswerService.saveAnswer(guestId, sessionId, requestDto);
        return ResponseEntity.ok(BasicResponseDto.success("응답 저장", null));
    }

}
