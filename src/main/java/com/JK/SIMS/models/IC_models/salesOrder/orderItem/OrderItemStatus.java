package com.JK.SIMS.models.IC_models.salesOrder.orderItem;

public enum OrderItemStatus {
    PENDING,           // Initial, awaiting processing
    PARTIALLY_APPROVED, // Partially approved for shipment
    APPROVED,          // Approved for shipment
    CANCELLED        // Order item was cancelled
}
