package com.JK.SIMS.service.InventoryServices.poService.searchLogic;

import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;

public interface PoStrategy {
    PaginatedResponse<SummaryPurchaseOrderView> searchInPos(String text, int page, int size, String sortBy, String sortDirection);
}
