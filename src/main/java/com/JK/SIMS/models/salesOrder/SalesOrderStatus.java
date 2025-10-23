package com.JK.SIMS.models.salesOrder;

public enum SalesOrderStatus {
    PENDING,    // When the Sales order is created will be on this status until confirmed
    PARTIALLY_DELIVERED, // When the order is partially shipped, will be on this status
    PARTIALLY_APPROVED, // When the order is partially confirmed in the IC, will be on this status

    //  ******* Complete statuses *******
    APPROVED, // After PENDING, when it is confirmed in the IC, will be on this status
    DELIVERY_IN_PROCESS, // It will be updated by scanning the QR code in the SalesOrder
    DELIVERED, // When the order is shipped, will be on this status. Will be updated by scanning the QR code
    CANCELLED // When the order is cancelled, will be on this status
}
