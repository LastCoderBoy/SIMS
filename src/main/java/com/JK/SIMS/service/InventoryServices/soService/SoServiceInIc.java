package com.JK.SIMS.service.InventoryServices.soService;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItemStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.soService.processSalesOrder.StockOutProcessor;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.SoFilterStrategy;
import com.JK.SIMS.service.utilities.salesOrderSearchLogic.SoSearchStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SoServiceInIc {

    private final Clock clock;
    private static final LocalDateTime URGENT_DELIVERY_DATE = LocalDateTime.now().plusDays(2);

    private final SecurityUtils securityUtils;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final GlobalServiceHelper globalServiceHelper;
    private final StockManagementLogic stockManagementLogic;
    private final StockOutProcessor stockOutProcessor;
    private final SalesOrderRepository salesOrderRepository;
    private final SoSearchStrategy icSoSearchStrategy;
    private final SoFilterStrategy filterWaitingSalesOrders;

    // Will be used in the SORT logic and the normal GET all logic.
    // Can be only sorted using Status.
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllOutgoingSalesOrders(@Min(0) int page,
                                                                              @Min(1) @Max(100) int size,
                                                                              String sortBy, String sortDir) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDir);
            Page<SalesOrder> salesOrders = salesOrderRepository.findAllOutgoingSalesOrders(pageable);
            log.info("IC-SO (getAllOutgoingSalesOrders): Returning {} paginated data", salesOrders.getContent().size());
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrders);
        } catch (Exception e) {
            log.error("IC-SO (getAllOutgoingSalesOrders): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }


    // TODO: Consider sending automatic message if there are urgent orders.
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        try {
            // Create the Pageable object with Sort.
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDir);

            // Get the page of urgent orders.
            Page<SalesOrder> entityResponse = salesOrderRepository.findAllUrgentSalesOrders(pageable, URGENT_DELIVERY_DATE);
            Page<SalesOrderResponseDto> dtoResponse = entityResponse.map(salesOrderServiceHelper::convertToSalesOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);
        } catch (DataAccessException da){
            log.error("OS (getAllUrgentSalesOrders): Failed to retrieve orders due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve orders due to database error", da);
        } catch (Exception e) {
            log.error("OS (getAllUrgentSalesOrders): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    // STOCK OUT button
    @Transactional
    public ApiResponse<Void> processSalesOrder(ProcessSalesOrderRequestDto requestDto, String jwtToken){
        try {
            String confirmedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            SalesOrder salesOrder = getSalesOrderById(requestDto.getOrderId());
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
    @Transactional
    public ApiResponse<Void> cancelSalesOrder(Long orderId, String jwtToken) {
        try {
            String cancelledBy = securityUtils.validateAndExtractUsername(jwtToken);
            SalesOrder salesOrder = getSalesOrderById(orderId);

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
        } catch (Exception e) {
            log.error("OS (cancelOrder): Error cancelling order - {}", e.getMessage());
            throw new ServiceException("Failed to cancel order", e);
        }
    }

    // Search by Customer Name or Order Reference ID
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> searchInWaitingSalesOrders(String text, int page, int size, String sortBy, String sortDir){
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if(text == null || text.trim().isEmpty()){
                log.warn("IcSo (searchInWaitingSalesOrders): Search text is null or empty, returning all waiting orders.");
                return getAllOutgoingSalesOrders(page, size, "id", "asc");
            }
            Page<SalesOrder> salesOrderPage = icSoSearchStrategy.searchInSo(text, page, size, sortBy, sortDir);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException ie) {
            log.error("OS (searchInWaitingSalesOrders): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            log.error("OS (searchInWaitingSalesOrders): Error searching orders - {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    private SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }

    @Transactional(readOnly = true)
    public Long countOutgoingSalesOrders() {
        try {
            return salesOrderRepository.countOutgoingSalesOrders();
        } catch (Exception e) {
            log.error("IC-SO (totalOutgoingStockSize): Error getting outgoing stock size - {}", e.getMessage());
            throw new ServiceException("Failed to get total outgoing stock size", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterWaitingSoProducts(SalesOrderStatus statusValue, String optionDateValue, LocalDate startDate,
                                                                            LocalDate endDate, int page, int size, String sortBy, String sortDirection) {
        try{
            // Validate and prepare the pageable
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            // Filter the orders
            Page<SalesOrder> salesOrderPage =
                    filterWaitingSalesOrders.filterSalesOrders(statusValue, optionDateValue, startDate, endDate, pageable);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException e) {
            log.error("IC-SO filterSalesOrders(): Invalid filter parameters: {}", e.getMessage(), e);
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("IC-SO filterSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }
}
