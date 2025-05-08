package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.response.GuestResponseDto;
import com.example.giftrecommender.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Guest", description = "비회원 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestService guestService;

    @Operation(summary = "비회원 생성", description = "UUID를 기반으로 비회원 세션을 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비회원 세션 생성 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping
    public ResponseEntity<BasicResponseDto<GuestResponseDto>> createGuest() {
        return ResponseEntity.ok(BasicResponseDto.success("비회원 세션 생성 완료.", guestService.createGuest()));
    }

}
