package com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderSummary {

    private Long totalAwaitingApproval;     // AWAITING_APPROVAL
    private Long totalDeliveryInProcess;    // DELIVERY_IN_PROCESS
    private Long totalPartiallyReceived;    // PARTIALLY_RECEIVED
    private Long totalReceived;             // RECEIVED
    private Long totalCancelled;            // CANCELLED
    private Long totalFailed;               // FAILED

    // Calculated fields
    public Long getTotalValid() {
        return totalAwaitingApproval + totalDeliveryInProcess + totalPartiallyReceived;
    }

    public Long getTotalOrders() {
        return totalAwaitingApproval + totalDeliveryInProcess + totalPartiallyReceived
                + totalReceived + totalCancelled + totalFailed;
    }

    public Double getSuccessRate() {
        Long total = getTotalOrders();
        return total > 0 ? (totalReceived * 100.0) / total : 0.0;
    }
}
