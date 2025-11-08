package com.JK.SIMS.service.reportAnalytics;

import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;

import java.math.BigDecimal;

public interface InventoryHealthService {
    InventoryReportMetrics getInventoryHealth();
    BigDecimal calculateInventoryStockValueAtRetail();
}
