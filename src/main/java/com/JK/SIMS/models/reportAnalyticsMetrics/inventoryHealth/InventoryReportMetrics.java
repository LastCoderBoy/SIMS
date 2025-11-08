package com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryReportMetrics {
    private BigDecimal totalStockValueAtRetail;
    private Long totalStockQuantity;
    private Long totalReservedStock;
    private Long availableStock;         // currentStock - reservedStock

    // ========== Stock Health Breakdown ==========
    private Long inStockItems;                  // currentStock > minLevel (healthy)
    private Long lowStockItems;                 // currentStock <= minLevel AND > 0 (warning)
    private Long outOfStockItems;               // currentStock = 0 (critical)

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

    public String getHealthStatus() {
        double score = getHealthScore();
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "GOOD";
        if (score >= 60) return "FAIR";
        if (score >= 40) return "POOR";
        return "CRITICAL";
    }
}
