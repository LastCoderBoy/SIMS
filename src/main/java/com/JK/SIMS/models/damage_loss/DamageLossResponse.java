package com.JK.SIMS.models.damage_loss;

import com.JK.SIMS.models.PM_models.ProductCategories;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DamageLossResponse(
        Integer id,
        String productName,
        ProductCategories category,
        String sku,
        Integer quantityLost,
        BigDecimal lossValue,
        LossReason reason,
        LocalDateTime lossDate
){
}
