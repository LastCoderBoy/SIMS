package com.JK.SIMS.service.InventoryServices.soService.searchLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import org.springframework.stereotype.Component;

@Component
public class OmSoSearchStrategy implements SoStrategy{
    @Override
    public PaginatedResponse<SalesOrderResponseDto> searchInSo(String text, int page, int size) {
        return null;
    }
}
