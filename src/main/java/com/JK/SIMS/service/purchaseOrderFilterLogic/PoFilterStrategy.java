package com.JK.SIMS.service.purchaseOrderFilterLogic;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import org.springframework.data.domain.Pageable;

public interface PoFilterStrategy {
    PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(ProductCategories category, PurchaseOrderStatus status, Pageable pageable);
}
