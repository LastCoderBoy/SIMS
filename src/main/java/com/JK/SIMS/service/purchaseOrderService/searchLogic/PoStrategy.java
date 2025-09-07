package com.JK.SIMS.service.purchaseOrderService.searchLogic;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.PaginatedResponse;
import org.springframework.data.domain.Page;

public interface PoStrategy {
    PaginatedResponse<PurchaseOrderResponseDto> searchInPos(String text, int page, int size);
}
