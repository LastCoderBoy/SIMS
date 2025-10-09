package com.JK.SIMS.service.purchaseOrderSearchLogic;

import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;

public interface PoSearchStrategy {
    PaginatedResponse<SummaryPurchaseOrderView> searchInPos(String text, int page, int size, String sortBy, String sortDirection);
}
