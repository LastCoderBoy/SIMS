package com.JK.SIMS.models.damage_loss;

import java.time.LocalDateTime;

public record DamageLossRequest(
    String sku,
    Integer quantityLost,
    LossReason reason,
    LocalDateTime lossDate
){}