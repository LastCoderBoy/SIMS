package com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderSearchLogic;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import org.springframework.data.domain.Page;

public interface PoSearchStrategy {
    Page<PurchaseOrder> searchInPos(String text, int page, int size, String sortBy, String sortDirection);
}
