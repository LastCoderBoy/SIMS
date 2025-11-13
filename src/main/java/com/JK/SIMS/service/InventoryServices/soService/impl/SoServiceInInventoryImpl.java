package com.JK.SIMS.service.InventoryServices.soService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.InventoryException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItemStatus;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInInventory;
import com.JK.SIMS.service.InventoryServices.soService.processSalesOrder.StockOutProcessor;
import com.JK.SIMS.service.salesOrder.salesOrderQueryService.SalesOrderQueryService;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.SalesOrderSearchService;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic.SoFilterStrategy;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderSearchLogic.SoSearchStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class SoServiceInInventoryImpl implements SoServiceInInventory {
    private final Clock clock;
    // =========== Helpers & Utilities ===========
    private final SecurityUtils securityUtils;

    // =========== Components ===========
    private final StockManagementLogic stockManagementLogic;
    private final StockOutProcessor stockOutProcessor;

    // =========== Services ===========
    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesOrderSearchService salesOrderSearchService;

    // =========== Repositories ===========
    private final SalesOrderRepository salesOrderRepository;


    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllOutgoingSalesOrders(@Min(0) int page,
                                                                              @Min(1) @Max(100) int size,
                                                                              String sortBy, String sortDir) {
        return salesOrderQueryService.getAllOutgoingSalesOrders(page, size, sortBy, sortDir);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        return salesOrderQueryService.getAllUrgentSalesOrders(page, size, sortBy, sortDir);
    }

    // STOCK OUT button
    @Override
    @Transactional
    public ApiResponse<Void> processSalesOrder(ProcessSalesOrderRequestDto requestDto, String jwtToken){
        try {
            String confirmedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            SalesOrder salesOrder = salesOrderQueryService.findById(requestDto.getOrderId());
            SalesOrder updatedSalesOrder = stockOutProcessor.processStockOut(
                    salesOrder, requestDto.getItemQuantities(), confirmedPerson
            );
            salesOrderRepository.save(updatedSalesOrder);
            log.info("OS (processOrderedProduct): SalesOrder {} processed successfully", updatedSalesOrder.getOrderReference());
            return new ApiResponse<>(true, "SalesOrder processed successfully");
        } catch (InventoryException | ResourceNotFoundException exc){
            throw exc;
        } catch (Exception e) {
            log.error("OS (processOrderedProduct): Error processing order - {}", e.getMessage());
            throw new ServiceException("Internal Service Error, failed to process order", e);
        }
    }

    // CANCEL button
    @Override
    @Transactional
    public ApiResponse<Void> cancelSalesOrder(Long orderId, String jwtToken) {
        try {
            String cancelledBy = securityUtils.validateAndExtractUsername(jwtToken);
            SalesOrder salesOrder = salesOrderQueryService.findById(orderId); // might throw ResourceNotFoundException

            if (salesOrder.getStatus() == SalesOrderStatus.PENDING || salesOrder.getStatus() == SalesOrderStatus.PARTIALLY_APPROVED) {
                // Release all reservations
                for (OrderItem item : salesOrder.getItems()) {
                    if(item.isFinalized()) {
                        stockManagementLogic.releaseReservation(item.getProduct().getProductID(), item.getQuantity() - item.getApprovedQuantity());
                        item.setStatus(OrderItemStatus.CANCELLED);
                    }
                }
                // Set the base fields
                salesOrder.setStatus(SalesOrderStatus.CANCELLED);
                salesOrder.setLastUpdate(GlobalServiceHelper.now(clock));
                salesOrder.setCancelledBy(cancelledBy);
                salesOrderRepository.save(salesOrder);

                log.info("OS (cancelOrder): SalesOrder {} cancelled successfully", orderId);
                return new ApiResponse<>(true, "SalesOrder cancelled successfully");
            }
            throw new ValidationException("Only Waiting orders can be cancelled.");
        } catch (ResourceNotFoundException | ValidationException exc){
            throw exc;
        } catch (Exception e) {
            log.error("OS (cancelOrder): Error cancelling order - {}", e.getMessage());
            throw new ServiceException("Failed to cancel order", e);
        }
    }

    // Search by Customer Name or Order Reference ID
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> searchInWaitingSalesOrders(String text, int page, int size, String sortBy, String sortDir){
        return salesOrderSearchService.searchPending(text, page, size, sortBy, sortDir);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterWaitingSoProducts(SalesOrderStatus statusValue, String optionDateValue,
                                                                            LocalDate startDate, LocalDate endDate, int page,
                                                                            int size, String sortBy, String sortDirection) {
        return salesOrderSearchService
                .filterPending(statusValue, optionDateValue, startDate, endDate, page, size, sortBy, sortDirection);
    }
}
