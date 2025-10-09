package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.BulkShipStockRequestDto;

public interface StockOutProcessor {
    ApiResponse processStockOut(BulkShipStockRequestDto bulkSoRequestDto, String username);
}
