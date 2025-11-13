package com.JK.SIMS.service.InventoryServices.soService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public interface SoServiceInInventory {

    PaginatedResponse<SummarySalesOrderView> getAllOutgoingSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size,
                                                                       String sortBy, String sortDir);

    PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size,
                                                                            String sortBy, String sortDir);

    ApiResponse<Void> processSalesOrder(ProcessSalesOrderRequestDto requestDto, String jwtToken);

    ApiResponse<Void> cancelSalesOrder(Long orderId, String jwtToken);

    PaginatedResponse<SummarySalesOrderView> searchInWaitingSalesOrders(String text, int page, int size, String sortBy, String sortDir);

    PaginatedResponse<SummarySalesOrderView> filterWaitingSoProducts(SalesOrderStatus statusValue, String optionDateValue, LocalDate startDate,
                                                                            LocalDate endDate, int page, int size, String sortBy, String sortDirection);
}
