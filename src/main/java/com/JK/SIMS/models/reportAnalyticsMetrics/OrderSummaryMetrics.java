package com.JK.SIMS.models.reportAnalyticsMetrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderSummaryMetrics {
    private SalesOrderSummary salesOrderSummary;
    private PurchaseOrderSummary purchaseOrderSummary;
}
