package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.dto.response.GuestResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class GuestServiceTest {

    @Autowired private GuestService guestService;

    @Autowired private GuestRepository guestRepository;

    @DisplayName("게스트가 생성되고 저장되어야 한다.")
    @Test
    void createAndStoreGuest() {
        // when
        GuestResponseDto response = guestService.createGuest();

        // then
        UUID guestId = response.guestId();
        Optional<Guest> savedGuest = guestRepository.findById(guestId);

        assertThat(savedGuest).isPresent();
        assertThat(savedGuest.get().getId()).isEqualTo(guestId);
    }

}