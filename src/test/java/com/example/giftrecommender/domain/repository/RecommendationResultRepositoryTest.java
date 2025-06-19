package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.entity.RecommendationResult;
import com.example.giftrecommender.domain.entity.RecommendationSession;
import com.example.giftrecommender.domain.enums.SessionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendationResultRepositoryTest {

    @Autowired private GuestRepository guestRepository;

    @Autowired private RecommendationSessionRepository recommendationSessionRepository;

    @Autowired private RecommendationResultRepository recommendationResultRepository;

    private Guest guest;

    private RecommendationSession recommendationSession;

    @BeforeEach
    void setUp() {
        guest = createGuest();
        guestRepository.save(guest);

        recommendationSession = createRecommendationSession(guest);
        recommendationSessionRepository.save(recommendationSession);
    }

    @AfterEach
    void tearDown() {
        recommendationResultRepository.deleteAllInBatch();
        recommendationSessionRepository.deleteAllInBatch();
        guestRepository.deleteAllInBatch();
    }

    @DisplayName("추천 세션 ID로 추천 결과를 조회할 수 있다.")
    @Test
    void findByRecommendationSessionId() {
        // given
        RecommendationResult recommendationResult = RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(recommendationSession)
                .build();
        recommendationResultRepository.save(recommendationResult);

        // when
        Optional<RecommendationResult> result =
                recommendationResultRepository.findByRecommendationSessionId(recommendationSession.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getRecommendationSession().getId()).isEqualTo(recommendationSession.getId());
    }

    private static RecommendationSession createRecommendationSession(Guest guest) {
        return RecommendationSession.builder()
                .id(UUID.randomUUID())
                .status(SessionStatus.PENDING)
                .guest(guest)
                .build();
    }

    private static Guest createGuest() {
        return Guest.builder()
                .id(UUID.randomUUID())
                .build();
    }

}