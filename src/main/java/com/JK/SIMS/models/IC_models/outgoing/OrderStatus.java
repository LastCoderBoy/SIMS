package com.JK.SIMS.models.IC_models.outgoing;

public enum OrderStatus {
    PENDING,    // When the Sales order is created will be on this status, until confirmed
    PROCESSING, // After PENDING.
    SHIPPED,
    COMPLETED,
    CANCELLED
}
