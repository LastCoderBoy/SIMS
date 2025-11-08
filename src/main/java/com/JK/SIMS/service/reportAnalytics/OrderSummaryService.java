package com.JK.SIMS.service.reportAnalytics;

import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.OrderSummaryMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.PurchaseOrderSummary;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.SalesOrderSummary;

public interface OrderSummaryService {
    OrderSummaryMetrics getOrderSummaryMetrics();
    SalesOrderSummary getSalesOrderSummary();
    PurchaseOrderSummary getPurchaseOrderSummary();
}
