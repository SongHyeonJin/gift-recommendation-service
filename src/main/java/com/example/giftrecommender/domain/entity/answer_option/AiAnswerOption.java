package com.example.giftrecommender.domain.entity.answer_option;

import com.example.giftrecommender.domain.entity.question.AiQuestion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ai_question_id", nullable = false)
    private AiQuestion question;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(nullable = false, length = 100)
    private String recommendationKeyword;

    // 프론트에서 선택한 선택지
    @Column(nullable = false)
    private int selectedIndex;

    @Builder
    public AiAnswerOption(AiQuestion question, String content, String recommendationKeyword, int selectedIndex) {
        this.question = question;
        this.content = content;
        this.recommendationKeyword = recommendationKeyword;
        this.selectedIndex = selectedIndex;
    }
}
