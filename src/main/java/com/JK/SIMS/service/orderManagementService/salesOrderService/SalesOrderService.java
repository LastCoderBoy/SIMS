package com.JK.SIMS.service.orderManagementService.salesOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.PaginatedResponse;

public interface SalesOrderService {
    PaginatedResponse<SummarySalesOrderView> getAllSummarySalesOrders(String sortBy, String sortDirection, int page, int size);
    DetailedSalesOrderView getDetailsForSalesOrderId(Long orderId);
    ApiResponse<String> createOrder(SalesOrderRequestDto salesOrderRequestDto, String jwtToken);
    ApiResponse<String> updateSalesOrder(Long orderId, SalesOrderRequestDto salesOrderRequestDto, String jwtToken);
    ApiResponse<String> cancelSalesOrder(Long orderId, String jwtToken);
}
