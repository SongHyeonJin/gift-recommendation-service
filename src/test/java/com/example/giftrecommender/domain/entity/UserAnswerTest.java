package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.AnswerOptionType;
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
                .type(QuestionType.FIXED)
                .order(1)
                .build();
        AnswerOption option = AnswerOption.builder()
                .content("이건 선택지입니다")
                .question(question)
                .build();
        String answerText = "이건 선택지입니다";

        // when
        UserAnswer answer = UserAnswer.ofFixed(
                guest,
                session,
                question,
                option,
                QuestionType.FIXED,
                AnswerOptionType.CHOICE,
                answerText
        );

        // then
        assertThat(answer).isNotNull();
        assertThat(answer.getGuest()).isEqualTo(guest);
        assertThat(answer.getRecommendationSession()).isEqualTo(session);
        assertThat(answer.getQuestion()).isEqualTo(question);
        assertThat(answer.getAnswerOption()).isEqualTo(option);
        assertThat(answer.getQuestionType()).isEqualTo(QuestionType.FIXED);
        assertThat(answer.getAnswerOptionType()).isEqualTo(AnswerOptionType.CHOICE);
        assertThat(answer.getAnswerText()).isEqualTo(answerText);
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
                .type(QuestionType.AI)
                .order(4)
                .build();
        AiAnswerOption aiAnswerOption = AiAnswerOption.builder()
                .question(aiQuestion)
                .content("GPT가 추천한 답변")
                .selectedIndex(1)
                .build();
        String answerText = "GPT가 추천한 답변";

        // when
        UserAnswer answer = UserAnswer.ofAi(
                guest,
                session,
                aiQuestion,
                aiAnswerOption,
                QuestionType.AI,
                AnswerOptionType.CHOICE,
                answerText
        );

        // then
        assertThat(answer).isNotNull();
        assertThat(answer.getGuest()).isEqualTo(guest);
        assertThat(answer.getRecommendationSession()).isEqualTo(session);
        assertThat(answer.getAiQuestion()).isEqualTo(aiQuestion);
        assertThat(answer.getAiAnswerOption()).isEqualTo(aiAnswerOption);
        assertThat(answer.getQuestionType()).isEqualTo(QuestionType.AI);
        assertThat(answer.getAnswerOptionType()).isEqualTo(AnswerOptionType.CHOICE);
        assertThat(answer.getAnswerText()).isEqualTo(answerText);
    }

    private static RecommendationSession createRecommendationSession(Guest guest) {
        return RecommendationSession.builder()
                .id(UUID.randomUUID())
                .guest(guest)
                .status(SessionStatus.PENDING)
                .build();
    }
}
