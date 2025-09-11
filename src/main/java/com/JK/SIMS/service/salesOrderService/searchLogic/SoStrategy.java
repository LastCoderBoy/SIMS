package com.JK.SIMS.service.salesOrderService.searchLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;

public interface SoStrategy {
    public PaginatedResponse<SalesOrderResponseDto> searchInSo(String text, int page, int size);
}
