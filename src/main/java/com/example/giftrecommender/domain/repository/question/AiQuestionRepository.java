package com.example.giftrecommender.domain.repository.question;

import com.example.giftrecommender.domain.entity.question.AiQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionRepository extends JpaRepository<AiQuestion, Long> {
}
