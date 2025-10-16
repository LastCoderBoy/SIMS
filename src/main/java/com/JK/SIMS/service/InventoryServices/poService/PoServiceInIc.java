package com.JK.SIMS.service.InventoryServices.poService;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.ReceiveStockRequestDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.service.purchaseOrderFilterLogic.PoFilterStrategy;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.productManagementService.PMServiceHelper;
import com.JK.SIMS.service.purchaseOrderSearchLogic.PoSearchStrategy;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;


@Service
public class PoServiceInIc {
    private static final String DEFAULT_SORT_BY = "product.name";
    private static final String DEFAULT_SORT_DIRECTION = "asc";
    private static final Logger logger = LoggerFactory.getLogger(PoServiceInIc.class);
    private final Clock clock;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SecurityUtils securityUtils;
    private final PurchaseOrderServiceHelper poServiceHelper;
    private final PoSearchStrategy poSearchStrategy;
    private final PoFilterStrategy poFilterStrategy;
    private final GlobalServiceHelper globalServiceHelper;
    private final PMServiceHelper pmServiceHelper;
    private final StockManagementLogic stockManagementLogic;
    private final StockMovementService stockMovementService; // Used to log the stock movement
    private final InventoryServiceHelper inventoryServiceHelper;
    @Autowired
    public PoServiceInIc(Clock clock, PurchaseOrderRepository purchaseOrderRepository, SecurityUtils securityUtils, PurchaseOrderServiceHelper poServiceHelper,
                         @Qualifier("icPoSearchStrategy") PoSearchStrategy poSearchStrategy, GlobalServiceHelper globalServiceHelper,
                         @Qualifier("pendingPoFilterStrategy") PoFilterStrategy poFilterStrategy, PMServiceHelper pmServiceHelper,
                         StockManagementLogic stockManagementLogic, StockMovementService stockMovementService, InventoryServiceHelper inventoryServiceHelper) {
        this.clock = clock;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.securityUtils = securityUtils;
        this.poServiceHelper = poServiceHelper;
        this.poSearchStrategy = poSearchStrategy;
        this.poFilterStrategy = poFilterStrategy;
        this.globalServiceHelper = globalServiceHelper;
        this.pmServiceHelper = pmServiceHelper;
        this.stockManagementLogic = stockManagementLogic;
        this.stockMovementService = stockMovementService;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPendingPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        try {
            String effectiveSortBy = (sortBy == null || sortBy.trim().isEmpty()) ? DEFAULT_SORT_BY : sortBy;
            String effectiveSortDirection = (sortDirection == null || sortDirection.trim().isEmpty()) ? DEFAULT_SORT_DIRECTION : sortDirection;

            Sort.Direction direction = effectiveSortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, effectiveSortBy));
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAllPendingOrders(pageable);
            PaginatedResponse<SummaryPurchaseOrderView> dtoResponse =
                    poServiceHelper.transformToPaginatedSummaryView(entityResponse);
            logger.info("PO (getAllPendingPurchaseOrders): Returning {} paginated data", dtoResponse.getContent().size());
            return dtoResponse;
        }catch (DataAccessException da){
            logger.error("PO (getAllPendingPurchaseOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("PO (getAllPendingPurchaseOrders): Database error", da);
        }catch (Exception e){
            logger.error("PO (getAllPendingPurchaseOrders): Service error occurred: {}", e.getMessage(), e);
            throw new ServiceException("PO (getAllPendingPurchaseOrders): Service error occurred", e);
        }
    }

    // STOCK IN button logic.
    @Transactional
    public ApiResponse<Void> receivePurchaseOrder(Long orderId, @Valid ReceiveStockRequestDto receiveRequest, String jwtToken) throws BadRequestException {
        try {
            String updatedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            poServiceHelper.validateOrderId(orderId); // check against null, throws an exception

            PurchaseOrder order = poServiceHelper.getPurchaseOrderById(orderId);

            if(order.isFinalized()){
                throw new ValidationException("PO (receivePurchaseOrder): Cannot receive stock for finalized order");
            }

            updateOrderWithReceivedStock(order, receiveRequest);
            updateInventoryLevels(order, receiveRequest.getReceivedQuantity());
            finalizeOrderUpdate(order, updatedPerson);
            stockMovementService.logMovement(
                    order.getProduct(), StockMovementType.IN,
                    receiveRequest.getReceivedQuantity(), order.getPONumber(),
                    StockMovementReferenceType.PURCHASE_ORDER, updatedPerson);

            logger.info("IS (receiveIncomingStock): Updated incoming stock order successfully. PO Number: {}", order.getPONumber());
            return new ApiResponse<>(true, "Incoming stock order updated successfully.");

        } catch (NumberFormatException e) {
            throw new ValidationException("IS (receiveIncomingStock): Invalid order ID format");
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("IS (receiveIncomingStock): " + e.getMessage());
        } catch (IllegalArgumentException | ValidationException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("IS (receiveIncomingStock): Unexpected error occurred: " + e.getMessage());
        }
    }

    private void updateOrderWithReceivedStock(PurchaseOrder order, ReceiveStockRequestDto receiveRequest) {
        // Set actual arrival date
        if (receiveRequest.getActualArrivalDate() != null) {
            if (receiveRequest.getActualArrivalDate().isAfter(LocalDate.now())) {
                throw new ValidationException("PO (receivePurchaseOrder): Actual arrival date cannot be in the future");
            }
            if (!receiveRequest.getActualArrivalDate().equals(order.getActualArrivalDate())) {
                order.setActualArrivalDate(receiveRequest.getActualArrivalDate());
            }
        } else if (order.getActualArrivalDate() == null) {
            order.setActualArrivalDate(GlobalServiceHelper.now(clock).toLocalDate());
        }

        // Update received quantity
        int quantityToReceive = receiveRequest.getReceivedQuantity();
        order.setReceivedQuantity(order.getReceivedQuantity() + quantityToReceive);

        // Update status based on received quantity
        updateOrderStatus(order);
    }

    private void updateOrderStatus(PurchaseOrder order) {
        if (order.getReceivedQuantity() >= order.getOrderedQuantity()) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
            pmServiceHelper.updateIncomingProductStatusInPm(order.getProduct());
        } else if (order.getReceivedQuantity() > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        // If no quantity received, status remains unchanged
    }

    private void updateInventoryLevels(PurchaseOrder order, int receivedQuantity) {
        if (receivedQuantity <= 0) {
            return; // No inventory update needed
        }
        try {
            Optional<InventoryControlData> inventoryProductOpt =
                    inventoryServiceHelper.getInventoryProductByProductId(order.getProduct().getProductID());
            if(inventoryProductOpt.isPresent()){
                InventoryControlData inventoryProduct = inventoryProductOpt.get();
                int newStockLevel = inventoryProduct.getCurrentStock() + receivedQuantity;

                // The service method to update stock levels with proper error handling
                stockManagementLogic.updateInventoryStockLevels(inventoryProduct,
                        Optional.of(newStockLevel),
                        Optional.empty());
            }
        } catch (Exception e) {
            throw new ServiceException("IS (updateIncomingStockOrder): Failed to update inventory levels: " + e.getMessage());
        }
    }

    private void finalizeOrderUpdate(PurchaseOrder order, String updatedPerson) {
        order.setUpdatedBy(updatedPerson);
        purchaseOrderRepository.save(order);
    }

    @Transactional
    public ApiResponse<Void> cancelPurchaseOrderInternal(Long orderId, String jwtToken) throws BadRequestException {
        try {
            poServiceHelper.validateOrderId(orderId); // check against null, throws an exception
            String user = securityUtils.validateAndExtractUsername(jwtToken);

            PurchaseOrder purchaseOrder = poServiceHelper.getPurchaseOrderById(orderId);

            // Cancellation only allowed if not already received or failed
            if (purchaseOrder.isFinalized()) {
                throw new ValidationException("PO (cancelPurchaseOrderInternal): Cannot cancel an incoming stock record with status: " + purchaseOrder.getStatus());
            }

            purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrder.setUpdatedBy(user);

            // Return back the Product Management section into the previous state
            pmServiceHelper.updateIncomingProductStatusInPm(purchaseOrder.getProduct());

            // Return back the Inventory Control into the previous state
            Optional<InventoryControlData> inventoryProductOpt =
                    inventoryServiceHelper.getInventoryProductByProductId(purchaseOrder.getProduct().getProductID());
            inventoryProductOpt.ifPresent(inventoryServiceHelper::updateInventoryStatus);

            purchaseOrderRepository.save(purchaseOrder);
            logger.info("PO (cancelPurchaseOrderInternal): SalesOrder cancelled successfully. PO Number: {}", purchaseOrder.getPONumber());
            return new ApiResponse<>(true, "The order cancelled successfully.");
        } catch (DataAccessException e) {
            logger.error("PO (cancelPurchaseOrderInternal): Database error while cancelling order", e);
            throw new DatabaseException("Failed to cancel order due to database error:" + e.getMessage());
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            logger.error("PO (cancelPurchaseOrderInternal): Unexpected error while cancelling order", e);
            throw new ServiceException("Failed to cancel order: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Long getTotalValidPoSize(){
        return purchaseOrderRepository.getTotalValidPoSize();
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchInIncomingPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if (text == null || text.trim().isEmpty()) {
                logger.warn("PO (searchInIncomingPurchaseOrders): Search text is null or empty, returning all incoming orders.");
                return getAllPendingPurchaseOrders(page, size, DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION);
            }
            return poSearchStrategy.searchInPos(text, page, size, sortBy, sortDirection);
        } catch (Exception e) {
            logger.error("PO (searchInIncomingPurchaseOrders): Error searching orders - {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterIncomingPurchaseOrders(PurchaseOrderStatus status, ProductCategories category,
                                                                           String sortBy, String sortDirection, int page, int size){
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create Pageable with the Sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            return poFilterStrategy.filterPurchaseOrders(category, status, pageable);
        } catch (Exception e) {
            logger.error("PO (filterIncomingPurchaseOrders): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> getAllOverduePurchaseOrders(int page, int size) {
        try {
            Sort sort = Sort.by(Sort.Direction.DESC, "product.name");
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<PurchaseOrder> allOverdueOrders = purchaseOrderRepository.findAllOverdueOrders(pageable);
            logger.info("PO (getAllOverduePurchaseOrders): Retrieved {} overdue orders", allOverdueOrders.getTotalElements());
            return poServiceHelper.transformToPaginatedDtoResponse(allOverdueOrders);
        } catch (DataAccessException dae) {
            logger.error("PO (getAllOverduePurchaseOrders): Database error while retrieving overdue orders", dae);
            throw new DatabaseException("Failed to retrieve overdue orders due to database error");
        } catch (Exception e) {
            logger.error("PO (getAllOverduePurchaseOrders): Unexpected error while retrieving overdue orders", e);
            throw new ServiceException("Failed to retrieve overdue orders: " + e.getMessage());
        }
    }
}
