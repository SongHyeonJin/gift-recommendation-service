package com.example.giftrecommender.controller;

import com.example.giftrecommender.dto.response.GuestResponseDto;
import com.example.giftrecommender.service.GuestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(GuestController.class)
class GuestControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private GuestService guestService;

    @DisplayName("POST /guests - 비회원 세션 생성 성공")
    @Test
    void createGuestSuccess() throws Exception {
        // given
        UUID testId = UUID.randomUUID();
        GuestResponseDto fakeResponse = new GuestResponseDto(testId);

        when(guestService.createGuest()).thenReturn(fakeResponse);

        // when  then
        mockMvc.perform(post("/api/guests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비회원 세션 생성 완료."))
                .andExpect(jsonPath("$.data.guestId").value(testId.toString()));
    }

}