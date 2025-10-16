package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;

import java.util.Map;

public interface StockOutProcessor {
    SalesOrder processStockOut(SalesOrder salesOrder, Map<String, Integer> approvedQuantities , String username);
}
