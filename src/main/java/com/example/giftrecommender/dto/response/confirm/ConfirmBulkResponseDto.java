package com.example.giftrecommender.dto.response.confirm;

import java.util.List;

public record ConfirmBulkResponseDto(
        int updatedCount,
        List<Long> updatedIds
) {}
