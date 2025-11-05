package com.JK.SIMS.models.damage_loss.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DamageLossMetrics {
    private Long totalReport;
    private Long totalItemLost;
    private BigDecimal totalLossValue;
}
