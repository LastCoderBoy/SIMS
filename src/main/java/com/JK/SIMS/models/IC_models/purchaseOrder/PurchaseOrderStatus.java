package com.JK.SIMS.models.IC_models.purchaseOrder;

public enum PurchaseOrderStatus {
    AWAITING_APPROVAL,  // New status for awaiting supplier confirmation
    DELIVERY_IN_PROCESS, // SalesOrder placed, awaiting arrival
    PARTIALLY_RECEIVED, // Some items received, more are expected
    RECEIVED,           // All ordered items have been received
    CANCELLED,          // SalesOrder was canceled before full receipt
    FAILED              // Delivery failed or was rejected
}
