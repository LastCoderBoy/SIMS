package com.JK.SIMS.models.damage_loss.dtos;

import com.JK.SIMS.models.PaginatedResponse;

import java.math.BigDecimal;

public record DamageLossPageResponse (
        Long totalReport,
        Long totalItems,
        BigDecimal totalLossValue,
        PaginatedResponse<DamageLossResponse> dtoResponse
){}
