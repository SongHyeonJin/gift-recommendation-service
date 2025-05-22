package com.example.giftrecommender.domain.repository.answer_option;

import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnswerOptionRepositoryTest {

    @Autowired
    private AnswerOptionRepository answerOptionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    private Question question;

    @BeforeEach
    void setUp() {
        question = questionRepository.save(
                Question.builder()
                        .content("취미는?")
                        .type(QuestionType.CHOICE)
                        .order(1)
                        .build()
        );
    }

    @DisplayName("특정 질문 ID에 대한 모든 선택지를 조회할 수 있다")
    @Test
    void findAllByQuestionId() {
        // given
        AnswerOption a1 = createAnswerOption("운동", "운동", question);
        AnswerOption a2 = createAnswerOption("독서", "책", question);

        answerOptionRepository.saveAll(List.of(a1, a2));

        // when
        List<AnswerOption> result = answerOptionRepository.findAllByQuestionId(question.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getQuestion().getId()).isEqualTo(question.getId());
    }

    private static AnswerOption createAnswerOption(String content, String recommendationKeyword, Question question) {
        return AnswerOption.builder()
                .content(content)
                .recommendationKeyword(recommendationKeyword)
                .question(question)
                .build();
    }
}