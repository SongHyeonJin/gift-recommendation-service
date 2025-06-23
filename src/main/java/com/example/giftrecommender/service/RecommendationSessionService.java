package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.entity.RecommendationSession;
import com.example.giftrecommender.domain.enums.SessionStatus;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.domain.repository.RecommendationSessionRepository;
import com.example.giftrecommender.dto.response.RecommendationSessionResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationSessionService {

    private final RecommendationSessionRepository recommendationSessionRepository;
    private final GuestRepository guestRepository;

    @Transactional
    public RecommendationSessionResponseDto createRecommendationSession(UUID guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> {
                    log.error("게스트 조회 실패: guestId={}", guestId);
                    return new ErrorException(ExceptionEnum.GUEST_NOT_FOUND);
                });

        RecommendationSession recommendationSession = RecommendationSession.builder()
                .id(UUID.randomUUID())
                .guest(guest)
                .status(SessionStatus.PENDING)
                .build();

        recommendationSessionRepository.save(recommendationSession);
        return new RecommendationSessionResponseDto(
                recommendationSession.getId()
        );
    }

}
