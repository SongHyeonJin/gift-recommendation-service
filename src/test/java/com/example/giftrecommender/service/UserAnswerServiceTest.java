package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.entity.RecommendationSession;
import com.example.giftrecommender.domain.entity.UserAnswer;
import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.domain.enums.SessionStatus;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.domain.repository.RecommendationSessionRepository;
import com.example.giftrecommender.domain.repository.UserAnswerRepository;
import com.example.giftrecommender.domain.repository.answer_option.AiAnswerOptionRepository;
import com.example.giftrecommender.domain.repository.answer_option.AnswerOptionRepository;
import com.example.giftrecommender.domain.repository.question.AiQuestionRepository;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import com.example.giftrecommender.dto.request.AnswerOptionRequestDto;
import com.example.giftrecommender.dto.request.QuestionRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerAiRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class UserAnswerServiceTest {

    @Autowired private UserAnswerService userAnswerService;

    @Autowired private GuestRepository guestRepository;

    @Autowired private RecommendationSessionRepository sessionRepository;

    @Autowired private QuestionRepository questionRepository;

    @Autowired private AnswerOptionRepository answerOptionRepository;

    @Autowired private AiQuestionRepository aiQuestionRepository;

    @Autowired private AiAnswerOptionRepository aiAnswerOptionRepository;

    @Autowired private UserAnswerRepository userAnswerRepository;

    private Guest guest;

    private RecommendationSession session;

    private List<Question> questions = new ArrayList<>();
    private List<AnswerOption> answerOptions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        guest = guestRepository.save(createGuest());
        session = sessionRepository.save(
                createRecommendationSession("테스트", guest));

        Question q1 = questionRepository.save(createQuestion("Q1 내용", 1));
        Question q2 = questionRepository.save(createQuestion("Q2 내용", 2));
        questions.addAll(List.of(q1,q2));
        questionRepository.saveAll(questions);

        AnswerOption a1 = answerOptionRepository.save(createAnswerOption("선택지1", q1));
        AnswerOption a2 = answerOptionRepository.save(createAnswerOption("선택지2", q1));
        AnswerOption a3 = answerOptionRepository.save(createAnswerOption("선택지1", q2));
        AnswerOption a4 = answerOptionRepository.save(createAnswerOption("선택지2", q2));
        AnswerOption a5 = answerOptionRepository.save(createAnswerOption("선택지3", q2));
        answerOptions.addAll(List.of(a1, a2, a3, a4, a5));
        answerOptionRepository.saveAll(answerOptions);
    }

    @AfterEach
    void tearDown() {
        userAnswerRepository.deleteAllInBatch();
        aiAnswerOptionRepository.deleteAllInBatch();
        aiQuestionRepository.deleteAllInBatch();
        answerOptionRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        guestRepository.deleteAllInBatch();
    }

    @DisplayName("고정형 답변을 저장할 수 있다.")
    @Test
    void saveUserAnswer() {
        // given
        Question question = questions.get(0);
        AnswerOption option = answerOptions.get(0);
        UserAnswerRequestDto request = new UserAnswerRequestDto(question.getId(), QuestionType.CHOICE, option.getId());

        // when
        userAnswerService.saveAnswer(guest.getId(), session.getId(), request);

        // then
        List<UserAnswer> saved = userAnswerRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getGuest().getId()).isEqualTo(guest.getId());
    }

    @DisplayName("AI 질문, 선택지와 답변을 저장할 수 있다.")
    @Test
    @Transactional
    void saveAiUserAnswer() {
        // given
        QuestionRequestDto requestDto = new QuestionRequestDto("AI 질문 내용", QuestionType.CHOICE, 4);
        AnswerOptionRequestDto option1 = new AnswerOptionRequestDto("1번 보기");
        AnswerOptionRequestDto option2 = new AnswerOptionRequestDto("2번 보기");
        UserAnswerAiRequestDto userAnswerAiRequestDto = new UserAnswerAiRequestDto(requestDto, List.of(option1, option2), 1);

        // when
        userAnswerService.saveAiQuestionAndAnswer(guest.getId(), session.getId(), userAnswerAiRequestDto);

        // then
        List<AiQuestion> savedQuestions = aiQuestionRepository.findAll();
        List<AiAnswerOption> savedOptions = aiAnswerOptionRepository.findAll();
        List<UserAnswer> savedAnswers = userAnswerRepository.findAll();

        assertThat(savedQuestions).hasSize(1);
        assertThat(savedOptions).hasSize(2);
        assertThat(savedAnswers).hasSize(1);
        assertThat(savedAnswers.get(0).getAiAnswerOption().getContent()).isEqualTo("2번 보기");
    }

    @DisplayName("게스트가 존재하지 않으면 예외가 발생한다")
    @Test
    void userAnswerGuestNotFound() {
        // given
        UUID invalidGuestId = UUID.randomUUID();
        Question question = questions.get(0);
        AnswerOption option = answerOptions.get(0);
        UserAnswerRequestDto request = new UserAnswerRequestDto(question.getId(), QuestionType.CHOICE, option.getId());

        // when & then
        assertThatThrownBy(() -> userAnswerService.saveAnswer(invalidGuestId, session.getId(), request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.GUEST_NOT_FOUND.getMessage());
    }

    @DisplayName("세션이 존재하지 않으면 예외가 발생한다.")
    @Test
    void userAnswerSessionNotFound() {
        // given
        UUID invalidSessionId = UUID.randomUUID();
        Question question = questions.get(0);
        AnswerOption option = answerOptions.get(0);
        UserAnswerRequestDto request = new UserAnswerRequestDto(question.getId(), QuestionType.CHOICE, option.getId());

        // when & then
        assertThatThrownBy(() -> userAnswerService.saveAnswer(guest.getId(), invalidSessionId, request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.SESSION_NOT_FOUND.getMessage());
    }

    @DisplayName("세션이 해당 게스트의 세션이 아니면 예외가 발생한다.")
    @Test
    void userAnswerSessionNotOwnedByGuest() {
        // given
        Guest otherGuest = guestRepository.save(createGuest());
        Question question = questions.get(0);
        AnswerOption option = answerOptions.get(0);
        UserAnswerRequestDto request = new UserAnswerRequestDto(question.getId(), QuestionType.CHOICE, option.getId());

        // when & then
        assertThatThrownBy(() -> userAnswerService.saveAnswer(otherGuest.getId(), session.getId(), request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.FORBIDDEN.getMessage());
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
                .type(QuestionType.CHOICE)
                .order(order)
                .build();
    }

    private static RecommendationSession createRecommendationSession(String name, Guest guest) {
        return RecommendationSession.builder()
                .id(UUID.randomUUID())
                .name(name)
                .status(SessionStatus.PENDING)
                .guest(guest)
                .build();
    }

    private static Guest createGuest() {
        return Guest.builder()
                .id(UUID.randomUUID())
                .build();
    }

}