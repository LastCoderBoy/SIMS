package com.JK.SIMS.models.damage_loss.dtos;

import com.JK.SIMS.models.damage_loss.LossReason;

import java.time.LocalDateTime;

public record DamageLossRequest(
    String sku,
    Integer quantityLost,
    LossReason reason,
    LocalDateTime lossDate
){}