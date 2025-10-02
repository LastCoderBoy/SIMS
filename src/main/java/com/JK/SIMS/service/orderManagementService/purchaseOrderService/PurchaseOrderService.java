package com.JK.SIMS.service.orderManagementService.purchaseOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderRequestDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import org.apache.coyote.BadRequestException;

public interface PurchaseOrderService {
    ApiResponse<PurchaseOrderRequestDto> createPurchaseOrder(PurchaseOrderRequestDto stockRequestDto, String jwtToken) throws BadRequestException;

    PaginatedResponse<PurchaseOrderResponseDto> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection);
}
