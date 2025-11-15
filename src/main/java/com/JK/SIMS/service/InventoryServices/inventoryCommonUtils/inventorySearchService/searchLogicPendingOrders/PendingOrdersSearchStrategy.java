package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventorySearchService.searchLogicPendingOrders;

import com.JK.SIMS.models.inventoryData.dtos.PendingOrdersResponseInIC;
import com.JK.SIMS.models.PaginatedResponse;

public interface PendingOrdersSearchStrategy {
    PaginatedResponse<PendingOrdersResponseInIC> searchInPendingOrders(String text, int page, int size);
}
