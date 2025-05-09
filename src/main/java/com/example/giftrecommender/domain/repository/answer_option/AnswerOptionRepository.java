package com.example.giftrecommender.domain.repository.answer_option;

import com.example.giftrecommender.domain.entity.answer_option.AnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {

    List<AnswerOption> findAllByQuestionId(Long questionId);

}
