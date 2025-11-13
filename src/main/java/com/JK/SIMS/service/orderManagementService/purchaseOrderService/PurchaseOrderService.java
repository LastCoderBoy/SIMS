package com.JK.SIMS.service.orderManagementService.purchaseOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.dtos.PurchaseOrderRequest;
import com.JK.SIMS.models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import org.apache.coyote.BadRequestException;

public interface PurchaseOrderService {
    ApiResponse<PurchaseOrderRequest> createPurchaseOrder(PurchaseOrderRequest stockRequestDto,
                                                          String jwtToken) throws BadRequestException;
    PaginatedResponse<SummaryPurchaseOrderView> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection);
    DetailsPurchaseOrderView getDetailsForPurchaseOrder(Long orderId);
    PaginatedResponse<SummaryPurchaseOrderView> searchPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection);
    PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(ProductCategories category, PurchaseOrderStatus status, String sortBy, String sortDirection, int page, int size);
}
