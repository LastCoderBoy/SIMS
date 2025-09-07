package com.JK.SIMS.models.IC_models.salesOrder;

public enum SalesOrderStatus {
    PENDING,    // When the Sales order is created will be on this status until confirmed
    PROCESSING, // After PENDING.
    PARTIALLY_SHIPPED,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
