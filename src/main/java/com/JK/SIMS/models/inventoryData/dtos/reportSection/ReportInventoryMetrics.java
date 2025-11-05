package com.JK.SIMS.models.inventoryData.dtos.reportSection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportInventoryMetrics {
    private BigDecimal totalStockValue;
    private Integer totalStockQuantity;
    private Integer lowStockItems;
    private Integer outOfStockItems;
    private Integer reservedStock;
    private Integer availableStock;
}
