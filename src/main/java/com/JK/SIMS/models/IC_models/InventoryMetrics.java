package com.JK.SIMS.models.IC_models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryMetrics {
    private Long totalCount;
    private Long lowStockCount;
    private Long incomingCount;
    private Long outgoingCount;
}