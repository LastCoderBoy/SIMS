package com.JK.SIMS.service.purchaseOrder.purchaseOrderQueryService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized Query Service for Purchase Order read operations
 * Purpose:
 * - Provides reusable read-only methods for PO queries
 * - Used by: POServiceInInventory, InventoryControlService, PurchaseOrderController
 * - Prevents code duplication across services
 * - Follows Single Responsibility Principle
 *
 * @author LastCoderBoy
 * @since 2025-01-12
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseOrderQueryService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderServiceHelper poServiceHelper;

    /**
     * Find purchase order by ID
     *
     * @param orderId purchase order ID
     * @return PurchaseOrder entity
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PurchaseOrder findById(Long orderId) {
        try {
            return purchaseOrderRepository.findById(orderId).orElseThrow(
                    () -> new ResourceNotFoundException("Purchase order not found with ID: " + orderId));
        } catch (DataAccessException e) {
            log.error("PO-Query (findById): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch purchase order", e);
        }
    }

    /**
     * Get all pending purchase orders with pagination and sorting
     * Pending = AWAITING_APPROVAL, DELIVERY_IN_PROCESS, PARTIALLY_RECEIVED
     * Used by:
     * - POServiceInInventoryImpl
     * - InventoryControlServiceImpl
     * - PurchaseOrderController
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param sortBy field to sort by (default: "product.name")
     * @param sortDirection "asc" or "desc" (default: "asc")
     * @return paginated summary view of pending orders
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPendingPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        try {
            // Handle null/empty sort parameters
            String effectiveSortBy = (sortBy == null || sortBy.trim().isEmpty())
                    ? "product.name"
                    : sortBy;
            String effectiveSortDirection = (sortDirection == null || sortDirection.trim().isEmpty())
                    ? "desc"
                    : sortDirection;

            // Create sort direction
            Sort.Direction direction = effectiveSortDirection.equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            // Create pageable and fetch data
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, effectiveSortBy));
            Page<PurchaseOrder> entityPage = purchaseOrderRepository.findAllPendingOrders(pageable);

            PaginatedResponse<SummaryPurchaseOrderView> response = poServiceHelper.transformToPaginatedSummaryView(entityPage);
            log.info("PO-Query (getAllPendingPurchaseOrders): Returned {} of {} pending orders",
                    response.getContent().size(),
                    response.getTotalElements());

            return response;

        } catch (DataAccessException e) {
            log.error("PO-Query (getAllPendingPurchaseOrders): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch pending purchase orders", e);
        } catch (Exception e) {
            log.error("PO-Query (getAllPendingPurchaseOrders): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to fetch pending purchase orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            // Retrieve the data and return the paginated response
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
            log.info("OM-PO (getAllPurchaseOrders): Returning {} paginated data", entityResponse.getContent().size());
            return poServiceHelper.transformToPaginatedSummaryView(entityResponse);
        } catch (DataAccessException da) {
            log.error("OM-PO (getAllPurchaseOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error", da);
        } catch (PropertyReferenceException e) {
            log.error("OM-PO (getAllPurchaseOrders): Invalid sort field provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid sort field provided: " + e.getMessage());
        } catch (Exception e) {
            log.error("OM-PO (getAllPurchaseOrders): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred:", e);
        }
    }

    @Transactional(readOnly = true)
    public DetailsPurchaseOrderView getDetailsForPurchaseOrder(Long orderId) throws ResourceNotFoundException {
        try {
            if(orderId == null || orderId < 1){
                throw new IllegalArgumentException("PO (getDetailsForPurchaseOrder): Invalid order ID provided: " + orderId);
            }
            PurchaseOrder purchaseOrder = findById(orderId);

            log.info("OM-PO (getDetailsForPurchaseOrder): Returning details for PO ID: {}", orderId);
            return new DetailsPurchaseOrderView(purchaseOrder);
        } catch (DataAccessException da) {
            log.error("OM-PO (getDetailsForPurchaseOrder): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error", da);
        } catch (ResourceNotFoundException e) {
            log.error("OM-PO (getDetailsForPurchaseOrder): Order ID {} not found: {}", orderId, e.getMessage(), e);
            throw new ResourceNotFoundException("Order ID " + orderId + " not found", e);
        } catch (IllegalArgumentException e) {
            log.error("OM-PO (getDetailsForPurchaseOrder): Invalid order ID provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid order ID provided: " + e.getMessage());
        } catch (Exception e) {
            log.error("OM-PO (getDetailsForPurchaseOrder): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred:", e);
        }
    }


    /**
     * Get total count of valid (pending) purchase orders
     * Valid = AWAITING_APPROVAL, DELIVERY_IN_PROCESS, PARTIALLY_RECEIVED
     */
    @Transactional(readOnly = true)
    public Long getTotalValidPoSize() {
        try {
            return purchaseOrderRepository.countIncomingPurchaseOrders();
        } catch (DataAccessException e) {
            log.error("PO-Query (getTotalValidPoSize): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to count valid purchase orders", e);
        }
    }

    /**
     * Get all overdue purchase orders
     * Overdue = expected arrival date < today AND status != RECEIVED/CANCELLED/FAILED
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllOverduePurchaseOrders(int page, int size) {
        try {
            Sort sort = Sort.by(Sort.Direction.DESC, "product.name");
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<PurchaseOrder> overduePage = purchaseOrderRepository.findAllOverdueOrders(pageable);

            log.info("PO-Query (getAllOverduePurchaseOrders): Found {} overdue orders",
                    overduePage.getTotalElements());

            return poServiceHelper.transformToPaginatedSummaryView(overduePage);

        } catch (DataAccessException e) {
            log.error("PO-Query (getAllOverduePurchaseOrders): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch overdue orders", e);
        } catch (Exception e) {
            log.error("PO-Query (getAllOverduePurchaseOrders): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to fetch overdue orders", e);
        }
    }


    /**
     * Save or update purchase order
     */
    @Transactional
    public PurchaseOrder save(PurchaseOrder purchaseOrder) {
        try {
            return purchaseOrderRepository.save(purchaseOrder);
        } catch (Exception e) {
            log.error("PO-Facade (save): Failed to save order - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to save purchase order", e);
        }
    }
}
