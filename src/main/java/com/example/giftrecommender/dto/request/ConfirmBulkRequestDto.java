package com.example.giftrecommender.dto.request;

import java.util.List;

public record ConfirmBulkRequestDto(
        List<Long> ids,
        Boolean isConfirmed
) {}
