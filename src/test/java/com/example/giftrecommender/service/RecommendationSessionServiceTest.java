package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.domain.repository.RecommendationSessionRepository;
import com.example.giftrecommender.dto.request.RecommendationSessionRequestDto;
import com.example.giftrecommender.dto.response.RecommendationSessionResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class RecommendationSessionServiceTest {

    @Autowired private RecommendationSessionService recommendationSessionService;
    @Autowired private GuestRepository guestRepository;
    @Autowired private RecommendationSessionRepository sessionRepository;

    private Guest guest;

    @BeforeEach
    void setUp() {
        guest = guestRepository.save(Guest.builder().id(UUID.randomUUID()).build());
    }

    @AfterEach
    void tearDown() {
        sessionRepository.deleteAllInBatch();
        guestRepository.deleteAllInBatch();
    }

    @DisplayName("게스트 ID로 추천 세션을 생성할 수 있다.")
    @Test
    void createRecommendationSessionSuccess() {
        // given
        RecommendationSessionRequestDto requestDto = new RecommendationSessionRequestDto("테스트");

        // when
        RecommendationSessionResponseDto response = recommendationSessionService.createRecommendationSession(guest.getId(), requestDto);

        // then
        assertThat(response).isNotNull();
        assertThat(response.recommendationSessionId()).isNotNull();
        assertThat(response.name()).isEqualTo("테스트");
    }

    @DisplayName("존재하지 않는 게스트 ID로 세션 생성 시 예외가 발생한다")
    @Test
    void createRecommendationSession_fail_whenGuestNotFound() {
        // given
        UUID invalidGuestId = UUID.randomUUID();
        RecommendationSessionRequestDto requestDto = new RecommendationSessionRequestDto("테스트");

        // when & then
        assertThatThrownBy(() ->
                recommendationSessionService.createRecommendationSession(invalidGuestId, requestDto)
        )
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.GUEST_NOT_FOUND.getMessage());
    }

}