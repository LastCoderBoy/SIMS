package com.JK.SIMS.service.InventoryServices.soService.searchLogic;

import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;

public interface SoStrategy {
    public PaginatedResponse<SalesOrderResponseDto> searchInSo(String text, int page, int size);
}
