package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import com.example.giftrecommender.dto.response.RecommendedProductResponseDto;
import com.example.giftrecommender.service.RecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private RecommendationService recommendationService;

    @MockBean private LogEventService logEventService;

    private UUID guestId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @DisplayName("POST /recommendation - 추천 요청 성공")
    @Test
    void recommendSuccess() throws Exception {
        // given
        RecommendationRequestDto request = new RecommendationRequestDto(
                "여자친구", "20대", Gender.FEMALE, 50000, 100000,
                "기념일", "악세서리", List.of("악세서리", "반지", "금")
        );

        RecommendedProductResponseDto product = new RecommendedProductResponseDto(
                UUID.randomUUID(),
                "골드 반지",
                99000,
                "https://example.com/product/1",
                "https://example.com/image.jpg",
                "mall",
                List.of("악세서리", "반지", "금")
        );

        RecommendationResponseDto fakeResponse = new RecommendationResponseDto(
                List.of(product)
        );

        when(recommendationService.recommend(eq(guestId), eq(sessionId), any(RecommendationRequestDto.class))).thenReturn(fakeResponse);

        // when  then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation", guestId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 완료"))
                .andExpect(jsonPath("$.data.products[0].title").value("골드 반지"))
                .andExpect(jsonPath("$.data.products[0].price").value(99000))
                .andExpect(jsonPath("$.data.products[0].link").value("https://example.com/product/1"))
                .andExpect(jsonPath("$.data.products[0].imageUrl").value("https://example.com/image.jpg"))
                .andExpect(jsonPath("$.data.products[0].keywords[1]").value("반지"));
    }

    @DisplayName("GET /recommendation - 추천 결과 조회 성공")
    @Test
    void getRecommendationResultSuccess() throws Exception {
        RecommendedProductResponseDto product = new RecommendedProductResponseDto(
                UUID.randomUUID(),
                "골드 반지",
                99000,
                "https://example.com/product/1",
                "https://example.com/image.jpg",
                "mall",
                List.of("악세서리", "반지", "금")
        );

        RecommendationResponseDto fakeResponse = new RecommendationResponseDto(
                List.of(product)
        );

        when(recommendationService.getRecommendationResult(eq(guestId), eq(sessionId))).thenReturn(fakeResponse);

        mockMvc.perform(get("/api/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation", guestId, sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 결과 조회 성공"))
                .andExpect(jsonPath("$.data.products[0].title").value("골드 반지"))
                .andExpect(jsonPath("$.data.products[0].price").value(99000))
                .andExpect(jsonPath("$.data.products[0].link").value("https://example.com/product/1"))
                .andExpect(jsonPath("$.data.products[0].imageUrl").value("https://example.com/image.jpg"))
                .andExpect(jsonPath("$.data.products[0].keywords[2]").value("금"));
    }
}