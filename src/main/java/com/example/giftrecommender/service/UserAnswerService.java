package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.AnswerOptionType;
import com.example.giftrecommender.domain.enums.QuestionType;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.domain.repository.answer_option.AiAnswerOptionRepository;
import com.example.giftrecommender.domain.repository.answer_option.AnswerOptionRepository;
import com.example.giftrecommender.domain.repository.question.AiQuestionRepository;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import com.example.giftrecommender.dto.request.AnswerOptionRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerAiRequestDto;
import com.example.giftrecommender.dto.request.UserAnswerRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
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

        Question question = questionRepository.findById(request.questionId()).orElseThrow(() -> {
            log.error("질문 ID 조회 실패 | questionId={}", request.questionId());
            return new ErrorException(ExceptionEnum.QUESTION_NOT_FOUND);
        });

        AnswerOption answerOption = null;
        String answerText = request.answerText();

        if (request.answerOptionType() == AnswerOptionType.CHOICE) {
            if (request.answerOptionId() == null) {
                throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
            }
            answerOption = optionRepository.findById(request.answerOptionId()).orElseThrow(
                    () -> new ErrorException(ExceptionEnum.OPTION_NOT_FOUND)
            );
            answerText = answerOption.getContent();
        }

        UserAnswer userAnswer = UserAnswer.ofFixed(
                guest, session, question, answerOption,
                request.questionType(), request.answerOptionType(), answerText
        );
        userAnswerRepository.save(userAnswer);
    }

    @Transactional
    public void saveAiQuestionAndAnswer(UUID guestId, UUID sessionId, UserAnswerAiRequestDto requestDto) {
        Guest guest = existsGuest(guestId);

        RecommendationSession session = existsRecommendationSession(sessionId);

        if (!session.getGuest().getId().equals(guest.getId())) {
            log.warn("세션 접근 권한 오류 | guestId={}, sessionId={}", guestId, sessionId);
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

        AiAnswerOption selectedOption = null;
        String answerText = requestDto.answerText();

        if (requestDto.answerOptionType() == AnswerOptionType.CHOICE) {
            if (requestDto.selectedIndex() == null || requestDto.selectedIndex() < 0 || requestDto.selectedIndex() >= options.size()) {
                throw new ErrorException(ExceptionEnum.INVALID_REQUEST);
            }
            selectedOption = options.get(requestDto.selectedIndex());
            answerText = selectedOption.getContent();
        }

        UserAnswer userAnswer = UserAnswer.ofAi(
                guest, session, question, selectedOption,
                QuestionType.AI, requestDto.answerOptionType(), answerText
        );
        userAnswerRepository.save(userAnswer);
    }

    private Guest existsGuest(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("게스트 조회 실패: guestId={}", id);
                    return new ErrorException(ExceptionEnum.GUEST_NOT_FOUND);
                });
    }

    private RecommendationSession existsRecommendationSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("추천 세션 조회 실패: sessionId={}", id);
                    return new ErrorException(ExceptionEnum.SESSION_NOT_FOUND);
                });
    }


}
