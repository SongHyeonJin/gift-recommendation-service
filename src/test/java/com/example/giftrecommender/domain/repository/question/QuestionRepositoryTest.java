package com.example.giftrecommender.domain.repository.question;

import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
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
class QuestionRepositoryTest {

    @Autowired
    private QuestionRepository questionRepository;

    @DisplayName("질문을 order 기준으로 오름차순 정렬하여 모두 조회할 수 있다")
    @Test
    void findAllByOrderByOrderAsc() {
        // given
        Question q1 = createQuestion("질문 1", 2);
        Question q2 = createQuestion("질문 2", 1);
        questionRepository.saveAll(List.of(q1, q2));

        // when
        List<Question> result = questionRepository.findAllByOrderByOrderAsc();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrder()).isEqualTo(1);
        assertThat(result.get(1).getOrder()).isEqualTo(2);
    }

    private static Question createQuestion(String content, int order) {
        return Question.builder()
                .content(content)
                .type(QuestionType.FIXED)
                .order(order)
                .build();
    }
}