package com.JK.SIMS.service.reportAnalytics;

import java.math.BigDecimal;

public interface InventoryHealthService {
    Long countStockQuantity();
    Long countReservedStockQuantity();
    Long countOutOfStockQuantity();
    Long countLowStockQuantity();
    Long countInStockQuantity();
    BigDecimal getInventoryStockValue();
}
