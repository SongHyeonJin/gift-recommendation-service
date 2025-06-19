package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.dto.response.RecommendationSessionResponseDto;
import com.example.giftrecommender.service.RecommendationSessionService;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(RecommendationSessionController.class)
class RecommendationSessionControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private RecommendationSessionService recommendationSessionService;

    @MockBean private LogEventService logEventService;

    private UUID guestId;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
    }

    @DisplayName("POST /recommendation-sessions - 추천 세션 생성 성공")
    @Test
    void createRecommendationSessionSuccess() throws Exception {
        // given
        UUID testSessionId = UUID.randomUUID();


        RecommendationSessionResponseDto fakeResponse = new RecommendationSessionResponseDto(
                testSessionId
        );

        when(recommendationSessionService.createRecommendationSession(guestId)).thenReturn(fakeResponse);

        // when  then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions", guestId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 세션 등록"));
    }

}