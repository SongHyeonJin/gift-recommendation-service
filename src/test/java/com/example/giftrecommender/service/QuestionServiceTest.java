package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.domain.repository.answer_option.AnswerOptionRepository;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import com.example.giftrecommender.dto.response.QuestionResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class QuestionServiceTest {

    @Autowired private QuestionService questionService;

    @Autowired private QuestionRepository questionRepository;

    @Autowired private AnswerOptionRepository answerOptionRepository;

    @DisplayName("질문과 선택지를 순서대로 조회할 수 있다.")
    @Test
    void getAllQuestionSuccess() {
        // given
        Question q1 = questionRepository.save(createQuestion("Q1 내용", 1));
        Question q2 = questionRepository.save(createQuestion("Q2 내용", 2));

        answerOptionRepository.save(createAnswerOption("선택지1", q1));
        answerOptionRepository.save(createAnswerOption("선택지2", q1));
        answerOptionRepository.save(createAnswerOption("선택지1", q2));
        answerOptionRepository.save(createAnswerOption("선택지2", q2));
        answerOptionRepository.save(createAnswerOption("선택지3", q2));

        // when
        List<QuestionResponseDto> result = questionService.getAllQuestion();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("Q1 내용");
        assertThat(result.get(0).options()).hasSize(2);
        assertThat(result.get(1).content()).isEqualTo("Q2 내용");
        assertThat(result.get(1).options()).hasSize(3);
    }

    private AnswerOption createAnswerOption(String content, Question question) {
        return AnswerOption.builder()
                .content(content)
                .question(question)
                .build();
    }

    private Question createQuestion(String content, Integer order) {
        return Question.builder()
                .content(content)
                .type(QuestionType.FIXED)
                .order(order)
                .build();
    }


}