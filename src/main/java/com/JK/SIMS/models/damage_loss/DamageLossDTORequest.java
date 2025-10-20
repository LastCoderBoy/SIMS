package com.JK.SIMS.models.damage_loss;

import java.time.LocalDateTime;

public record DamageLossDTORequest (
    String sku,
    Integer quantityLost,
    LossReason reason,
    LocalDateTime lossDate
){}