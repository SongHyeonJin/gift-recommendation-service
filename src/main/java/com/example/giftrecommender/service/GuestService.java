package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.Guest;
import com.example.giftrecommender.domain.repository.GuestRepository;
import com.example.giftrecommender.dto.response.GuestResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;

    @Transactional
    public GuestResponseDto createGuest() {
        UUID uuid = UUID.randomUUID();
        Guest guest = Guest.builder()
                .id(uuid)
                .build();

        guestRepository.save(guest);
        return new GuestResponseDto(uuid);
    }

}
