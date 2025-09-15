package com.JK.SIMS.service.salesOrderService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.BulkShipStockRequestDto;

public interface StockOutProcessor {
    ApiResponse processStockOut(BulkShipStockRequestDto bulkSoRequestDto, String username);
}
