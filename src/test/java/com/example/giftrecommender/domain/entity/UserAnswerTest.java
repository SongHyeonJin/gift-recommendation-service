package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.domain.enums.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class UserAnswerTest {

    @DisplayName("고정 질문용 ofFixed 생성자 테스트")
    @Test
    void testOfFixed() {
        // given
        Guest guest = new Guest(UUID.randomUUID());
        RecommendationSession session = createRecommendationSession(guest);
        Question question = Question.builder()
                .content("이건 고정 질문입니다")
                .type(QuestionType.CHOICE)
                .order(1)
                .build();
        AnswerOption option = AnswerOption.builder()
                .content("이건 선택지입니다")
                .question(question)
                .build();

        // when
        UserAnswer answer = UserAnswer.ofFixed(guest, session, question, option, QuestionType.CHOICE);

        // then
        assertThat(answer).isNotNull();
        assertThat(answer.getGuest()).isEqualTo(guest);
        assertThat(answer.getRecommendationSession()).isEqualTo(session);
        assertThat(answer.getQuestion()).isEqualTo(question);
        assertThat(answer.getAnswerOption()).isEqualTo(option);
        assertThat(answer.getType()).isEqualTo(QuestionType.CHOICE);
    }

    private static RecommendationSession createRecommendationSession(Guest guest) {
        RecommendationSession session = RecommendationSession.builder()
                .id(UUID.randomUUID())
                .guest(guest)
                .status(SessionStatus.PENDING)
                .build();
        return session;
    }

    @DisplayName("GPT 질문용 ofAi 생성자 테스트")
    @Test
    void testOfAi() {
        // given
        Guest guest = new Guest(UUID.randomUUID());
        RecommendationSession session = createRecommendationSession(guest);
        AiQuestion aiQuestion = AiQuestion.builder()
                .guest(guest)
                .session(session)
                .content("AI 질문입니다")
                .type(QuestionType.CHOICE)
                .order(4)
                .build();
        AiAnswerOption aiAnswerOption = AiAnswerOption.builder()
                .question(aiQuestion)
                .content("GPT가 추천한 답변")
                .selectedIndex(1)
                .build();

        // when
        UserAnswer answer = UserAnswer.ofAi(guest, session, aiQuestion, aiAnswerOption, QuestionType.CHOICE);

        // then
        assertThat(answer).isNotNull();
        assertThat(answer.getGuest()).isEqualTo(guest);
        assertThat(answer.getRecommendationSession()).isEqualTo(session);
        assertThat(answer.getAiQuestion()).isEqualTo(aiQuestion);
        assertThat(answer.getAiAnswerOption()).isEqualTo(aiAnswerOption);
        assertThat(answer.getType()).isEqualTo(QuestionType.CHOICE);
    }

}