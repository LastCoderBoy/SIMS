package com.JK.SIMS.service.orderManagementService.purchaseOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderRequestDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import org.apache.coyote.BadRequestException;

public interface PurchaseOrderService {
    ApiResponse<PurchaseOrderRequestDto> createPurchaseOrder(PurchaseOrderRequestDto stockRequestDto,
                                                             String jwtToken) throws BadRequestException;
    PaginatedResponse<SummaryPurchaseOrderView> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection);
    DetailsPurchaseOrderView getDetailsForPurchaseOrderId(Long orderId);
    PaginatedResponse<SummaryPurchaseOrderView> searchPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection);
    PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(String category, String status, String sortBy, String sortDirection, int page, int size);
}
