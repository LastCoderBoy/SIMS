package com.JK.SIMS.service.InventoryServices.poService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.dtos.ReceiveStockRequest;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;

public interface POServiceInInventory {
    PaginatedResponse<SummaryPurchaseOrderView> getAllPendingPurchaseOrders(int page, int size, String sortBy, String sortDirection);

    ApiResponse<Void> receivePurchaseOrder(Long orderId, @Valid ReceiveStockRequest receiveRequest, String jwtToken) throws BadRequestException;

    ApiResponse<Void> cancelPurchaseOrderInternal(Long orderId, String jwtToken) throws BadRequestException;

    PaginatedResponse<SummaryPurchaseOrderView> searchInIncomingPurchaseOrders(String text, int page, int size,
                                                                               String sortBy, String sortDirection);

    PaginatedResponse<SummaryPurchaseOrderView> filterIncomingPurchaseOrders(PurchaseOrderStatus status, ProductCategories category,
                                                                             String sortBy, String sortDirection, int page, int size);

    PaginatedResponse<SummaryPurchaseOrderView> getAllOverduePurchaseOrders(int page, int size);
}
