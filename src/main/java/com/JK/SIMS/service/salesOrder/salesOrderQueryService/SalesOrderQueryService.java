package com.JK.SIMS.service.salesOrder.salesOrderQueryService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


/**
 * Shared query service for product-related read operations
 * Purpose: Break circular dependencies between SalesOrderService and other services
 * Contains ONLY read operations - no business logic or state changes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderQueryService {
    private static final LocalDateTime URGENT_DELIVERY_DATE = LocalDateTime.now().plusDays(2);

    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final SalesOrderRepository salesOrderRepository;


    @Transactional(readOnly = true)
    public SalesOrder findById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with ID: " + orderId + " not found"));
    }

    /**
     * Count active orders for product
     */
    @Transactional(readOnly = true)
    public long countActiveOrdersForProduct(String productId) {
        return salesOrderRepository.countActiveOrdersForProduct(productId);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllOutgoingSalesOrders(int page, int size, String sortBy, String sortDir) {
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

    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(int page, int size, String sortBy, String sortDir) {
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

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> getAllSummarySalesOrders(String sortBy, String sortDirection, int page, int size) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            Page<SalesOrder> salesOrderPage = salesOrderRepository.findAll(pageable);
            log.info("OM-SO (getAllSummarySalesOrders): Returning {} paginated data", salesOrderPage.getContent().size());

            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (DataAccessException da){
            log.error("OM-SO (getAllSummarySalesOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error occurred, please contact the administration");
        } catch (PropertyReferenceException e) {
            log.error("OM-SO (getAllSummarySalesOrders): Invalid sort field provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid sort field provided. Check your request");
        } catch (Exception e) {
            log.error("OM-SO (getAllSummarySalesOrders): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Transactional(readOnly = true)
    public DetailedSalesOrderView getDetailsForSalesOrder(Long orderId) {
        try {
            globalServiceHelper.validateOrderId(orderId, salesOrderRepository, "SalesOrder"); // might throw ValidationException
            SalesOrder salesOrder = findById(orderId);
            log.info("Returning detailed salesOrder view for ID: {}", orderId);
            return new DetailedSalesOrderView(salesOrder);
        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (DataAccessException da) {
            log.error("OM-SO (getDetailsForSalesOrderId): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error occurred, please contact the administration");
        } catch (Exception e) {
            log.error("OM-SO (getDetailsForSalesOrderId): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
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
    public Long countInProgressSalesOrders() {
        try {
            return salesOrderRepository.countInProgressSalesOrders();
        } catch (DataAccessException e) {
            log.error("OM-SO (getTotalSalesOrdersCount): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to count sales orders", e);
        }
    }

}
