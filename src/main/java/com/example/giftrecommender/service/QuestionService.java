package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.repository.answer_option.AnswerOptionRepository;
import com.example.giftrecommender.domain.repository.question.QuestionRepository;
import com.example.giftrecommender.dto.response.AnswerOptionResponseDto;
import com.example.giftrecommender.dto.response.QuestionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;

    @Transactional(readOnly = true)
    public List<QuestionResponseDto> getAllQuestion() {
        return questionRepository.findAllByOrderByOrderAsc().stream()
                .map(question -> {
                    List<AnswerOptionResponseDto> options = answerOptionRepository.findAllByQuestionId(question.getId()).stream()
                            .map(option -> new AnswerOptionResponseDto(
                                    option.getId(),
                                    option.getContent(),
                                    option.getRecommendationKeyword()))
                            .toList();

                    return new QuestionResponseDto(
                            question.getId(),
                            question.getContent(),
                            question.getType(),
                            question.getOrder(),
                            options
                    );
                })
                .toList();
    }

}
