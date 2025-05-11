package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.RecommendationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationSessionRepository extends JpaRepository<RecommendationSession, UUID> {

    Optional<RecommendationSession> findTopByGuestIdOrderByCreatedAtDesc(UUID guestId);

}
