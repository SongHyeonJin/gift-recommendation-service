package com.example.giftrecommender.domain.repository.log;

import com.example.giftrecommender.domain.entity.log.LogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogRepository extends JpaRepository<LogEntity, Long> {
}
