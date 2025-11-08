package com.JK.SIMS.service.reportAnalytics;

import com.JK.SIMS.models.reportAnalyticsMetrics.*;
import com.JK.SIMS.models.reportAnalyticsMetrics.financial.FinancialOverviewMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.OrderSummaryMetrics;

import java.time.LocalDate;

public interface ReportAnalyticsService {
    /**
     * Get main dashboard with all key metrics
     */
    DashboardMetrics getMainDashboardMetrics();

    /**
     * Get detailed inventory health metrics
     */
    InventoryReportMetrics getInventoryHealth();

    /**
     * Get financial overview by predefined time range
     */
    FinancialOverviewMetrics getFinancialOverview(TimeRange timeRange);

    /**
     * Get financial overview by custom date range
     */
    FinancialOverviewMetrics getFinancialOverview(LocalDate startDate, LocalDate endDate);

    /**
     * Get order summary (both sales and purchase orders)
     */
    OrderSummaryMetrics getOrderSummary();
}
