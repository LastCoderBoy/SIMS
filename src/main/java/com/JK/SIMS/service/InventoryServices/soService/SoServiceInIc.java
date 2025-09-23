package com.JK.SIMS.service.InventoryServices.soService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.BulkShipStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.soService.filterLogic.SalesOrderSpecification;
import com.JK.SIMS.service.InventoryServices.soService.processSalesOrder.StockOutProcessor;
import com.JK.SIMS.service.InventoryServices.soService.searchLogic.SoStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class SoServiceInIc {

    private static final Logger logger = LoggerFactory.getLogger(SoServiceInIc.class);

    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final GlobalServiceHelper globalServiceHelper;
    private final StockManagementLogic stockManagementLogic;
    private final SoStrategy searchStrategy;
    private final StockOutProcessor stockOutProcessor;

    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public SoServiceInIc(SalesOrderServiceHelper salesOrderServiceHelper, GlobalServiceHelper globalServiceHelper,
                         StockManagementLogic stockManagementLogic, @Qualifier("icSoSearchStrategy") SoStrategy searchStrategy,
                         StockOutProcessor stockOutProcessor, SalesOrderRepository salesOrderRepository) {
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
    public PaginatedResponse<SalesOrderResponseDto> getAllWaitingSalesOrders(@Min(0) int page,
                                                                             @Min(1) @Max(100) int size,
                                                                             String sortBy, String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SalesOrder> salesOrders = salesOrderRepository.findAllWaitingSalesOrders(pageable);

            Page<SalesOrderResponseDto> dtoResponse = salesOrders.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);

        } catch (Exception e) {
            logger.error("OS (getAllSalesOrdersSorted): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    // Urgent Shipment table (CurrentDate + 2 > estimatedDeliveryDate)
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        try {
            // Create the Pageable object with Sort.
            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            // Get the page of urgent orders.
            Page<SalesOrder> entityResponse = salesOrderRepository.findAllUrgentSalesOrders(pageable);
            Page<SalesOrderResponseDto> dtoResponse = entityResponse.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);
        } catch (DataAccessException da){
            logger.error("OS (getAllUrgentSalesOrders): Failed to retrieve orders due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve orders due to database error", da);
        } catch (Exception e) {
            logger.error("OS (getAllUrgentSalesOrders): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    // STOCK OUT button
    @Transactional
    public ApiResponse processOrderRequest(BulkShipStockRequestDto requestDto, String jwtToken){
        try {
            String confirmedPerson = globalServiceHelper.validateAndExtractUser(jwtToken);
            return stockOutProcessor.processStockOut(requestDto, confirmedPerson);
        } catch (Exception e) {
            logger.error("OS (processOrderedProduct): Error processing order - {}", e.getMessage());
            throw new ServiceException("Failed to process order", e);
        }
    }

    // CANCEL button
    @Transactional
    public ApiResponse cancelSalesOrder(Long orderId, String jwtToken) {
        try {
            String cancelledBy = globalServiceHelper.validateAndExtractUser(jwtToken);
            SalesOrder salesOrder = getSalesOrderById(orderId);

            if (salesOrder.getStatus() == SalesOrderStatus.PENDING || salesOrder.getStatus() == SalesOrderStatus.PARTIALLY_APPROVED
                    || salesOrder.getStatus() == SalesOrderStatus.PARTIALLY_SHIPPED) {
                // Release all reservations
                for (OrderItem item : salesOrder.getItems()) {
                    stockManagementLogic.releaseReservation(item.getProduct().getProductID(), item.getQuantity());
                }

                salesOrder.setStatus(SalesOrderStatus.CANCELLED);
                salesOrder.setConfirmedBy(cancelledBy);
                salesOrderRepository.save(salesOrder);

                logger.info("OS (cancelOrder): SalesOrder {} cancelled successfully", orderId);
                return new ApiResponse(true, "SalesOrder cancelled successfully");
            }
            throw new ValidationException("Only Waiting orders can be cancelled.");
        } catch (Exception e) {
            logger.error("OS (cancelOrder): Error cancelling order - {}", e.getMessage());
            throw new ServiceException("Failed to cancel order", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> searchInOutgoingSalesOrders(String text, int page, int size){
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if(text == null || text.trim().isEmpty()){
                logger.warn("IcSo (searchInOutgoingSalesOrders): Search text is null or empty, returning all waiting orders.");
                return getAllWaitingSalesOrders(page, size, "id", "asc");
            }
            return searchStrategy.searchInSo(text, page, size);
        } catch (IllegalArgumentException ie) {
            logger.error("OS (searchInOutgoingSalesOrders): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            logger.error("OS (searchInOutgoingSalesOrders): Error searching orders - {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    @Transactional(readOnly = true)
    public SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found"));
    }

    // internal Helper methods
    @Transactional(readOnly = true)
    public Long getTotalValidOutgoingStockSize() {
        try {
            return salesOrderRepository.getOutgoingValidStockSize();
        } catch (Exception e) {
            logger.error("SO (totalOutgoingStockSize): Error getting outgoing stock size - {}", e.getMessage());
            throw new ServiceException("Failed to get total outgoing stock size", e);
        }
    }

    // Average Fulfill Time.
    // Method for future reference.
    @Transactional(readOnly = true)
    public int getAverageFulfillTime() {
        try {
            long totalEntities = salesOrderRepository.count();
            if(totalEntities == 0) return 0;
            long totalDeliveryDate = salesOrderRepository.calculateTotalDeliveryDate();
            return (int) (totalDeliveryDate / totalEntities);
        } catch (DataAccessException da) {
            logger.error("OS (getAverageFulfillTime): Failed to calculate average fulfill time: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to calculate average fulfill time", da);
        } catch (Exception e) {
            logger.error("OS (getAverageFulfillTime): Error calculating average fulfill time: {}", e.getMessage());
            throw new ServiceException("Failed to calculate average fulfill time", e);
        }
    }
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> filterSoProducts(SalesOrderStatus status, String optionDate, LocalDate startDate, LocalDate endDate, int page, int size) {
        try{
            globalServiceHelper.validatePaginationParameters(page, size);
            // Always filtered by the allowed statuses
            Specification<SalesOrder> specification = Specification.where(
                    SalesOrderSpecification.byWaitingStatus()
            );
            // Filtering by status if provided
            if(status != null){
                if(status == SalesOrderStatus.PENDING || status == SalesOrderStatus.APPROVED){
                    specification = specification.and(SalesOrderSpecification.byStatus(status));
                }
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
            Page<SalesOrderResponseDto> dtoResponse = entityResponse.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);
        } catch (IllegalArgumentException ie) {
            logger.error("OS (filterSoProductsByStatus): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            logger.error("OS (filterSoProductsByStatus): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}
