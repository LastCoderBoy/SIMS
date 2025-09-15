package com.JK.SIMS.models.IC_models.salesOrder;

public enum SalesOrderStatus {
    PENDING,    // When the Sales order is created will be on this status until confirmed
    PARTIALLY_SHIPPED, // When the order is partially shipped, will be on this status
    PARTIALLY_APPROVED, // When the order is partially confirmed in the IC, will be on this status

    //  ******* Complete statuses *******
    APPROVED, // After PENDING, when it is confirmed in the IC, will be on this status
    SHIPPED, // When the order is shipped, will be on this status
    COMPLETED, // When the customer confirms the order receive, will be on this status
    CANCELLED // When the order is cancelled, will be on this status
}
