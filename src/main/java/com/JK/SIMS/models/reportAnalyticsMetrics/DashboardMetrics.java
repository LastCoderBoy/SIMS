package com.JK.SIMS.models.reportAnalyticsMetrics;

import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DashboardMetrics (
        Long totalActiveProducts,
        Long totalInactiveProducts,
        BigDecimal totalInventoryStockValue,
        Long totalInProgressSalesOrders,
        Long totalValidPurchaseOrders,
        Long totalDamagedProducts
){}
