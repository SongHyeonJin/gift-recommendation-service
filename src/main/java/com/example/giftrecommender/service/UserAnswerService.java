package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.domain.repository.answer_option.AiAnswerOptionRepository;
import com.example.giftrecommender.domain.repository.answer_option.AnswerOptionRepository;
import com.example.giftrecommender.domain.repository.question.AiQuestionRepository;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import com.example.giftrecommender.dto.request.AnswerOptionRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerAiRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UserAnswerService {

    private final GuestRepository guestRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository optionRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final AiQuestionRepository aiQuestionRepository;
    private final AiAnswerOptionRepository aiAnswerOptionRepository;

    @Transactional
    public void saveAnswer(UUID guestId, UUID sessionId, UserAnswerRequestDto request) {
        Guest guest = existsGuest(guestId);

        RecommendationSession session = existsRecommendationSession(sessionId);

        if (!session.getGuest().getId().equals(guest.getId())) {
            throw new ErrorException(ExceptionEnum.FORBIDDEN);
        }

        Question question = questionRepository.findById(request.questionId()).orElseThrow(
                () -> new ErrorException(ExceptionEnum.QUESTION_NOT_FOUND)
        );

        AnswerOption option = optionRepository.findById(request.answerOptionId()).orElseThrow(
                () -> new ErrorException(ExceptionEnum.OPTION_NOT_FOUND)
        );

        UserAnswer userAnswer = UserAnswer.ofFixed(guest, session, question, option, request.type());
        userAnswerRepository.save(userAnswer);
    }

    @Transactional
    public void saveAiQuestionAndAnswer(UUID guestId, UUID sessionId, UserAnswerAiRequestDto requestDto) {
        Guest guest = existsGuest(guestId);

        RecommendationSession session = existsRecommendationSession(sessionId);

        if (!session.getGuest().getId().equals(guest.getId())) {
            throw new ErrorException(ExceptionEnum.FORBIDDEN);
        }

        AiQuestion question = AiQuestion.builder()
                .guest(guest)
                .session(session)
                .content(requestDto.question().content())
                .type(requestDto.question().type())
                .order(requestDto.question().order())
                .build();
        aiQuestionRepository.save(question);

        List<AiAnswerOption> options = IntStream.range(0, requestDto.options().size())
                .mapToObj(i -> {
                    AnswerOptionRequestDto opt = requestDto.options().get(i);
                    return AiAnswerOption.builder()
                            .question(question)
                            .content(opt.content())
                            .selectedIndex(i)
                            .build();
                })
                .toList();
        aiAnswerOptionRepository.saveAll(options);

        AiAnswerOption selectedOption = options.get(requestDto.selectedIndex());

        UserAnswer userAnswer = UserAnswer.ofAi(guest, session, question, selectedOption, requestDto.question().type());
        userAnswerRepository.save(userAnswer);
    }

    private Guest existsGuest(UUID guestId) {
        return guestRepository.findById(guestId).orElseThrow(
                () -> new ErrorException(ExceptionEnum.GUEST_NOT_FOUND)
        );
    }

    private RecommendationSession existsRecommendationSession(UUID sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow(
                () -> new ErrorException(ExceptionEnum.SESSION_NOT_FOUND)
        );
    }


}
