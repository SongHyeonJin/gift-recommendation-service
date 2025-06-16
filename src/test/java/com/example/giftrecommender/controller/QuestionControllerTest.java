package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.dto.response.AnswerOptionResponseDto;
import com.example.giftrecommender.dto.response.QuestionResponseDto;
import com.example.giftrecommender.service.QuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(QuestionController.class)
class QuestionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private QuestionService questionService;

    @MockBean private LogEventService logEventService;

    @DisplayName("GET /questions - 질문 목록 조회 성공")
    @Test
    void getQuestionsSuccess() throws Exception {
        // given
        List<AnswerOptionResponseDto> optionList = List.of(
                new AnswerOptionResponseDto(1L,"연인"),
                new AnswerOptionResponseDto(2L, "부모님")
        );

        List<QuestionResponseDto> fakeResponse = List.of(
                new QuestionResponseDto(
                        1L,
                        "누구에게 선물하나요?",
                        QuestionType.CHOICE,
                        1,
                        optionList
                )
        );

        when(questionService.getAllQuestion()).thenReturn(fakeResponse);

        // when  then
        mockMvc.perform(get("/api/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("질문 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].content").value("누구에게 선물하나요?"))
                .andExpect(jsonPath("$.data[0].type").value("CHOICE"))
                .andExpect(jsonPath("$.data[0].order").value(1))
                .andExpect(jsonPath("$.data[0].options[0].content").value("연인"));
    }

}