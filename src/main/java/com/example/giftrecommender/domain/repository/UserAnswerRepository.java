package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {
}
