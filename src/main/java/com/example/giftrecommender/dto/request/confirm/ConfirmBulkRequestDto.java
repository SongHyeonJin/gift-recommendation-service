package com.example.giftrecommender.dto.request.confirm;

import java.util.List;

public record ConfirmBulkRequestDto(
        List<Long> ids,
        Boolean isConfirmed
) {}
