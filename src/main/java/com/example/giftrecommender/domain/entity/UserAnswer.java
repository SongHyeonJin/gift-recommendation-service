package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.answer_option.AiAnswerOption;
import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import com.example.giftrecommender.domain.entity.question.AiQuestion;
import com.example.giftrecommender.domain.entity.question.Question;
import com.example.giftrecommender.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAnswer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_answer_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne
    @JoinColumn(name = "recommendation_session_id", nullable = false)
    private RecommendationSession recommendationSession;

    // 고정 질문(1~3번)
    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne
    @JoinColumn(name = "answer_option_id")
    private AnswerOption answerOption;

    // GPT 기반 질문(4~6번)
    @ManyToOne
    @JoinColumn(name = "ai_question_id")
    private AiQuestion aiQuestion;

    @ManyToOne
    @JoinColumn(name = "ai_answer_option_id")
    private AiAnswerOption aiAnswerOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    private LocalDateTime createdAt;

    // 고정 질문 생성자
    public UserAnswer(Guest guest, RecommendationSession recommendationSession,
                      Question question, AnswerOption answerOption, QuestionType type) {
        this.guest = guest;
        this.recommendationSession = recommendationSession;
        this.question = question;
        this.answerOption = answerOption;
        this.type = type;
    }

    public static UserAnswer ofFixed(Guest guest, RecommendationSession recommendationSession,
                                     Question question, AnswerOption answerOption, QuestionType type) {
        return new UserAnswer(guest, recommendationSession, question, answerOption, type);
    }

    // GPT 질문 생성자
    public UserAnswer(Guest guest, RecommendationSession recommendationSession,
                      AiQuestion aiQuestion, AiAnswerOption aiAnswerOption, QuestionType type) {
        this.guest = guest;
        this.recommendationSession = recommendationSession;
        this.aiQuestion = aiQuestion;
        this.aiAnswerOption = aiAnswerOption;
        this.type = type;
    }


    public static UserAnswer ofAi(Guest guest, RecommendationSession recommendationSession,
                      AiQuestion aiQuestion, AiAnswerOption aiAnswerOption, QuestionType type) {
        return new UserAnswer(guest, recommendationSession, aiQuestion, aiAnswerOption, type);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
