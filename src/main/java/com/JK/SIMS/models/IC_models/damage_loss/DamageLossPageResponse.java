package com.JK.SIMS.models.IC_models.damage_loss;

import com.JK.SIMS.models.PaginatedResponse;

import java.math.BigDecimal;

public record DamageLossPageResponse (
        Long totalReport,
        Long totalItems,
        BigDecimal totalLossValue,
        PaginatedResponse<DamageLossDTO> dtoResponse
){}
