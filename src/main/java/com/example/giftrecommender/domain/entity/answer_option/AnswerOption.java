package com.example.giftrecommender.domain.entity.answer_option;

import com.example.giftrecommender.domain.entity.question.Question;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerOption {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_option_id")
    private Long id;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false, length = 100)
    private String recommendationKeyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Builder
    public AnswerOption(String content, String recommendationKeyword, Question question) {
        this.content = content;
        this.recommendationKeyword = recommendationKeyword;
        this.question = question;
    }
}
