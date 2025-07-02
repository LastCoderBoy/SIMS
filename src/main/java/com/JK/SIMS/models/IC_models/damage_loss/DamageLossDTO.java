package com.JK.SIMS.models.IC_models.damage_loss;

import com.JK.SIMS.models.PM_models.ProductCategories;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DamageLossDTO (
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
