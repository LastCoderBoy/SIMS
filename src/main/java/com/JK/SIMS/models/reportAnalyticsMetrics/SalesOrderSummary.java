package com.JK.SIMS.models.reportAnalyticsMetrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderSummary {

    private Long totalPending;              // PENDING
    private Long totalDeliveryInProcess;    // DELIVERY_IN_PROCESS
    private Long totalDelivered;            // DELIVERED
    private Long totalApproved;             // APPROVED
    private Long totalPartiallyApproved;    // PARTIALLY_APPROVED
    private Long totalPartiallyDelivered;   // PARTIALLY_DELIVERED
    private Long totalCancelled;            // CANCELLED

    // Calculated fields
    public Long getTotalInProgress() {
        return totalPending + totalDeliveryInProcess + totalApproved
                + totalPartiallyApproved + totalPartiallyDelivered;
    }

    public Long getTotalOrders() {
        return totalPending + totalDeliveryInProcess + totalDelivered
                + totalApproved + totalPartiallyApproved + totalPartiallyDelivered
                + totalCancelled;
    }

    public Double getCompletionRate() {
        Long total = getTotalOrders();
        return total > 0 ? (totalDelivered * 100.0) / total : 0.0;
    }
}
