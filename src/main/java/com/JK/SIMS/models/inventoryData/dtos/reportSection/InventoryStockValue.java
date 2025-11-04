package com.JK.SIMS.models.inventoryData.dtos.reportSection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryStockValue {
    private BigDecimal totalStockValue;
}
