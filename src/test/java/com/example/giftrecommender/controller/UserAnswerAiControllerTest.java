package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.domain.enums.AnswerOptionType;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.dto.request.AnswerOptionRequestDto;
import com.example.giftrecommender.dto.request.QuestionRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerAiRequestDto;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(UserAnswerAiController.class)
class UserAnswerAiControllerTest {

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

    @DisplayName("POST /ai-answers - 선택형 응답 저장 성공")
    @Test
    void saveAiChoiceAnswerSuccess() throws Exception {
        // given
        QuestionRequestDto question = new QuestionRequestDto("연인의 취미가 뭐야?", QuestionType.AI, 4);
        List<AnswerOptionRequestDto> options = List.of(
                new AnswerOptionRequestDto("캠핑"),
                new AnswerOptionRequestDto("운동"),
                new AnswerOptionRequestDto("영화")
        );

        UserAnswerAiRequestDto requestDto = new UserAnswerAiRequestDto(
                question,
                options,
                1,
                null,
                AnswerOptionType.CHOICE
        );

        doNothing().when(userAnswerService).saveAiQuestionAndAnswer(guestId, sessionId, requestDto);

        // when & then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions/{sessionId}/ai-answers", guestId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 질문, 선택지 저장 완료 및 유저 응답 완료"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("POST /ai-answers - 직접입력형 응답 저장 성공")
    @Test
    void saveAiTextAnswerSuccess() throws Exception {
        // given
        QuestionRequestDto question = new QuestionRequestDto("그 사람이 좋아하는 선물은?", QuestionType.AI, 5);
        List<AnswerOptionRequestDto> options = List.of(
                new AnswerOptionRequestDto("실용적"),
                new AnswerOptionRequestDto("감성적"),
                new AnswerOptionRequestDto("가성비")
        );

        UserAnswerAiRequestDto requestDto = new UserAnswerAiRequestDto(
                question,
                options,
                null,
                "트렌디한 것",
                AnswerOptionType.TEXT
        );

        doNothing().when(userAnswerService).saveAiQuestionAndAnswer(guestId, sessionId, requestDto);

        // when & then
        mockMvc.perform(post("/api/guests/{guestId}/recommendation-sessions/{sessionId}/ai-answers", guestId, sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 질문, 선택지 저장 완료 및 유저 응답 완료"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
