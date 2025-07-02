package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.AnswerOptionType;
import com.example.giftrecommender.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAnswer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_answer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_session_id", nullable = false)
    private RecommendationSession recommendationSession;

    // 고정 질문(1~3번)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_option_id")
    private AnswerOption answerOption;

    // GPT 기반 질문(4~6번)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_question_id")
    private AiQuestion aiQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_answer_option_id")
    private AiAnswerOption aiAnswerOption;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_option_type", nullable = false, length = 20)
    private AnswerOptionType answerOptionType;

    @Column(name = "answer_text", length = 300)
    private String answerText;

    private Instant createdAt;

    // 고정 질문 생성자
    public UserAnswer(Guest guest, RecommendationSession recommendationSession, Question question, AnswerOption answerOption,
                      QuestionType questionType, AnswerOptionType answerOptionType, String answerText) {
        this.guest = guest;
        this.recommendationSession = recommendationSession;
        this.question = question;
        this.answerOption = answerOption;
        this.questionType = questionType;
        this.answerOptionType = answerOptionType;
        this.answerText = answerText;
    }

    public static UserAnswer ofFixed(Guest guest, RecommendationSession recommendationSession,
                                     Question question, AnswerOption answerOption,
                                     QuestionType questionType, AnswerOptionType answerOptionType, String answerText) {
        return new UserAnswer(guest, recommendationSession, question, answerOption, questionType, answerOptionType, answerText);
    }

    // GPT 질문 생성자
    public UserAnswer(Guest guest, RecommendationSession recommendationSession, AiQuestion aiQuestion, AiAnswerOption aiAnswerOption,
                      QuestionType questionType, AnswerOptionType answerOptionType, String answerText) {
        this.guest = guest;
        this.recommendationSession = recommendationSession;
        this.aiQuestion = aiQuestion;
        this.aiAnswerOption = aiAnswerOption;
        this.questionType = questionType;
        this.answerOptionType = answerOptionType;
        this.answerText = answerText;
    }


    public static UserAnswer ofAi(Guest guest, RecommendationSession recommendationSession,
                                  AiQuestion aiQuestion, AiAnswerOption aiAnswerOption,
                                  QuestionType questionType, AnswerOptionType answerOptionType, String answerText) {
        return new UserAnswer(guest, recommendationSession, aiQuestion, aiAnswerOption, questionType, answerOptionType, answerText);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

}
