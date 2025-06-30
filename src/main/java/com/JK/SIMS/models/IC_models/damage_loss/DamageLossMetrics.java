package com.JK.SIMS.models.IC_models.damage_loss;

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
