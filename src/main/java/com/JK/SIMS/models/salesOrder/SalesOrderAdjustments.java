package com.JK.SIMS.models.salesOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderAdjustments {
    private String productId;
    private int quantity;
    private boolean wasReserved; // true if we reserved, false if we released
}
