package com.JK.SIMS.models.IC_models.incoming;

public enum IncomingStockStatus {
    PENDING,            // Order placed, awaiting arrival
    PARTIALLY_RECEIVED, // Some items received, more are expected
    RECEIVED,           // All ordered items have been received
    CANCELLED,          // Order was cancelled before full receipt
    FAILED              // Delivery failed or was rejected
}
