package com.JK.SIMS.models.reportAnalyticsModels;

import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DashboardMetrics (
    ReportProductMetrics totalActiveInactivatedProducts,
    BigDecimal totalInventoryStockValue,
    Long totalInProgressSalesOrders,
    Long totalValidPurchaseOrders,
    Long totalDamagedProducts
){}
