package com.JK.SIMS.models.inventoryData.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryMetrics {
    private Long totalCount;
    private Long lowStockCount;
}