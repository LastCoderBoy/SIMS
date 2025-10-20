package com.JK.SIMS.service.orderManagementService.salesOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.BulkOrderItemsRequestDto;
import com.JK.SIMS.models.PaginatedResponse;
import jakarta.validation.Valid;

import java.time.LocalDate;

public interface SalesOrderService {
    PaginatedResponse<SummarySalesOrderView> getAllSummarySalesOrders(String sortBy, String sortDirection, int page, int size);
    DetailedSalesOrderView getDetailsForSalesOrderId(Long orderId);
    ApiResponse<String> createOrder(@Valid SalesOrderRequestDto salesOrderRequestDto, String jwtToken);
    ApiResponse<String> updateSalesOrder(Long orderId, SalesOrderRequestDto salesOrderRequestDto, String jwtToken);
    ApiResponse<String> addItemsToSalesOrder(Long orderId, @Valid BulkOrderItemsRequestDto bulkOrderItemsRequestDto, String jwtToken);
    ApiResponse<String> removeItemFromSalesOrder(Long orderId, Long itemId, String jwtToken);
    PaginatedResponse<SummarySalesOrderView> searchInSalesOrders(String text, int page, int size, String sortBy, String sortDirection);
    PaginatedResponse<SummarySalesOrderView> filterSalesOrders(SalesOrderStatus soStatus, String optionDateValue, LocalDate startDate, LocalDate endDate, int page, int size, String sortBy, String sortDirection);
}
