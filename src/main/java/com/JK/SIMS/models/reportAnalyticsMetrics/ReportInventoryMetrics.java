package com.JK.SIMS.models.reportAnalyticsMetrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportInventoryMetrics {
    private BigDecimal totalStockValue;
    private Long totalStockQuantity;
    private Long totalReservedStock;
    private Long availableStock;         // currentStock - reservedStock
    private Long outOfStockItems;        // currentStock = 0
    private Long lowStockItems;          // currentStock <= minLevel AND > 0
    private Long inStockItems;           // currentStock > minLevel;

    // Calculated fields
    public Double getStockUtilization() {
        return totalStockQuantity > 0
                ? (totalReservedStock * 100.0) / totalStockQuantity
                : 0.0;
    }

    public Double getHealthScore() {
        // Simple health score: 100 - (low stock % * 0.5 + out of stock % * 1.0)
        long totalItems = inStockItems + lowStockItems + outOfStockItems;
        if (totalItems == 0) return 100.0;

        double lowStockPenalty = (lowStockItems * 100.0 / totalItems) * 0.5;
        double outOfStockPenalty = (outOfStockItems * 100.0 / totalItems) * 1.0;

        return Math.max(0, 100.0 - lowStockPenalty - outOfStockPenalty);
    }
}
