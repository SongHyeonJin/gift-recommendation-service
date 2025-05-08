package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.entity.RecommendationSession;
import com.example.giftrecommender.domain.enums.SessionStatus;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.domain.repository.RecommendationSessionRepository;
import com.example.giftrecommender.dto.request.RecommendationSessionRequestDto;
import com.example.giftrecommender.dto.response.RecommendationSessionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationSessionService {

    private final RecommendationSessionRepository recommendationSessionRepository;
    private final GuestRepository guestRepository;

    @Transactional
    public RecommendationSessionResponseDto createRecommendationSession(RecommendationSessionRequestDto requestDto) {
        Guest guest = guestRepository.findById(requestDto.guestId()).orElseThrow(
                () -> new ErrorException(ExceptionEnum.GUEST_NOT_FOUND)
        );

        RecommendationSession recommendationSession = RecommendationSession.builder()
                .id(UUID.randomUUID())
                .guest(guest)
                .status(SessionStatus.PENDING)
                .build();

        recommendationSessionRepository.save(recommendationSession);
        return new RecommendationSessionResponseDto(recommendationSession.getId());
    }


}
