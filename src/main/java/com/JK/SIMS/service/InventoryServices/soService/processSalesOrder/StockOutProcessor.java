package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;

public interface StockOutProcessor {
    ApiResponse<String> processStockOut(ProcessSalesOrderRequestDto bulkSoRequestDto, String username);
}
