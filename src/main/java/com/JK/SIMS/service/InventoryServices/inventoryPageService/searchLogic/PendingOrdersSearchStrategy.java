package com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic;

import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.PaginatedResponse;

public interface PendingOrdersSearchStrategy {
    PaginatedResponse<PendingOrdersResponseDto> searchInPendingOrders(String text, int page, int size);
}
