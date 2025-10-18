package com.JK.SIMS.service.InventoryServices.soService;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.filterSpecification.SalesOrderSpecification;
import com.JK.SIMS.service.InventoryServices.soService.processSalesOrder.StockOutProcessor;
import com.JK.SIMS.service.utilities.salesOrderSearchLogic.SoSearchStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SoServiceInIc {

    private final Clock clock;
    private static final LocalDateTime URGENT_DELIVERY_DATE = LocalDateTime.now().plusDays(2);

    private final SecurityUtils securityUtils;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final GlobalServiceHelper globalServiceHelper;
    private final StockManagementLogic stockManagementLogic;
    private final SoSearchStrategy searchStrategy;
    private final StockOutProcessor stockOutProcessor;

    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public SoServiceInIc(Clock clock, SecurityUtils securityUtils, SalesOrderServiceHelper salesOrderServiceHelper, GlobalServiceHelper globalServiceHelper,
                         StockManagementLogic stockManagementLogic, @Qualifier("icSoSearchStrategy") SoSearchStrategy searchStrategy,
                         StockOutProcessor stockOutProcessor, SalesOrderRepository salesOrderRepository) {
        this.clock = clock;
        this.securityUtils = securityUtils;
        this.salesOrderServiceHelper = salesOrderServiceHelper;
        this.globalServiceHelper = globalServiceHelper;
        this.stockManagementLogic = stockManagementLogic;
        this.searchStrategy = searchStrategy;
        this.stockOutProcessor = stockOutProcessor;
        this.salesOrderRepository = salesOrderRepository;
    }

    // Will be used in the SORT logic and the normal GET all logic.
    // Can be only sorted using Status.
    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllWaitingSalesOrders(@Min(0) int page,
                                                                             @Min(1) @Max(100) int size,
                                                                             String sortBy, String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SalesOrder> salesOrders = salesOrderRepository.findAllWaitingSalesOrders(pageable);
            log.info("IC-SO (getAllWaitingSalesOrders): Returning {} paginated data", salesOrders.getContent().size());
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrders);
        } catch (Exception e) {
            log.error("IC-SO (getAllSalesOrdersSorted): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }


    // TODO: Consider sending automatic message if there are urgent orders.
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        try {
            // Create the Pageable object with Sort.
            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

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
    public ApiResponse<Void> processOrderRequest(ProcessSalesOrderRequestDto requestDto, String jwtToken){
        try {
            String confirmedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            SalesOrder salesOrder = getSalesOrderById(requestDto.getOrderId());
            SalesOrder updatedSalesOrder = stockOutProcessor.processStockOut(
                    salesOrder, requestDto.getItemQuantities(), confirmedPerson
            );
            salesOrderRepository.save(updatedSalesOrder);
            log.info("OS (processOrderedProduct): SalesOrder {} processed successfully", updatedSalesOrder.getOrderReference());
            return new ApiResponse<>(true, "SalesOrder processed successfully");
        } catch (InventoryException ie){
            throw ie;
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
                return getAllWaitingSalesOrders(page, size, "id", "asc");
            }
            Page<SalesOrder> salesOrderPage = searchStrategy.searchInSo(text, page, size, sortBy, sortDir);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException ie) {
            log.error("OS (searchInWaitingSalesOrders): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            log.error("OS (searchInWaitingSalesOrders): Error searching orders - {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    @Transactional(readOnly = true)
    public SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found"));
    }

    @Transactional(readOnly = true)
    public Long getWaitingStockSize() {
        try {
            return salesOrderRepository.getWaitingStockSize();
        } catch (Exception e) {
            log.error("IC-SO (totalOutgoingStockSize): Error getting outgoing stock size - {}", e.getMessage());
            throw new ServiceException("Failed to get total outgoing stock size", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterSoProducts(SalesOrderStatus status, String optionDate,
                                                                     LocalDate startDate, LocalDate endDate, int page, int size) {
        try{
            globalServiceHelper.validatePaginationParameters(page, size);
            // Always filtered by the allowed statuses
            Specification<SalesOrder> specification = Specification.where(
                    SalesOrderSpecification.byWaitingStatus()
            );

            // Filtering by status if provided
            if(status != null){
                specification = specification.and(
                        SalesOrderSpecification.byStatus(status));
            }

            // Filtering by dates
            if(optionDate != null && !optionDate.isEmpty()){
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("Start date and end date must be provided for date filtering.");
                }
                if (startDate.isAfter(endDate)) {
                    throw new IllegalArgumentException("Start date must be before or equal to end date.");
                }
                String option = optionDate.toLowerCase().trim();
                specification = specification.and(SalesOrderSpecification.byDatesBetween(option, startDate, endDate));
            }

            // Database call and conversion to DTO
            Pageable pageable = PageRequest.of(page, size);
            Page<SalesOrder> entityResponse = salesOrderRepository.findAll(specification, pageable);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(entityResponse);

        } catch (IllegalArgumentException ie) {
            log.error("OS (filterSoProductsByStatus): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            log.error("OS (filterSoProductsByStatus): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}
