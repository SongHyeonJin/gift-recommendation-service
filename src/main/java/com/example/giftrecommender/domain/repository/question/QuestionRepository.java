package com.example.giftrecommender.domain.repository.question;

import com.example.giftrecommender.domain.entity.question.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByOrderByOrderAsc();

}
