package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.domain.enums.AnswerOptionType;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.dto.request.UserAnswerRequestDto;
import com.example.giftrecommender.service.UserAnswerService;
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

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(UserAnswerController.class)
class UserAnswerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserAnswerService userAnswerService;
    @MockBean private LogEventService logEventService;

    private UUID guestId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @DisplayName("POST /answers - 유저 응답 저장 성공 (선택형)")
    @Test
    void saveUserAnswerSuccess_choice() throws Exception {
        // given
        UserAnswerRequestDto requestDto = new UserAnswerRequestDto(
                1L,
                QuestionType.FIXED,
                AnswerOptionType.CHOICE,
                1L,
                null
        );

        doNothing().when(userAnswerService).saveAnswer(guestId, sessionId, requestDto);

        // when & then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions/{sessionId}/answers", guestId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("응답 저장"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("POST /answers - 유저 응답 저장 성공 (직접입력형)")
    @Test
    void saveUserAnswerSuccess_text() throws Exception {
        // given
        UserAnswerRequestDto requestDto = new UserAnswerRequestDto(
                2L,
                QuestionType.FIXED,
                AnswerOptionType.TEXT,
                null,
                "직접 입력한 답변"
        );

        doNothing().when(userAnswerService).saveAnswer(guestId, sessionId, requestDto);

        // when & then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions/{sessionId}/answers", guestId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("응답 저장"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
