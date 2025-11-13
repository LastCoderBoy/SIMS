package com.JK.SIMS.service.InventoryServices.poService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.dtos.ReceiveStockRequestDto;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryUtils.InventoryStatusModifier;
import com.JK.SIMS.service.InventoryServices.poService.POServiceInInventory;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderQueryService.PurchaseOrderQueryService;
import com.JK.SIMS.service.productManagementService.utils.productStatusModifier.ProductStatusModifier;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.PurchaseOrderSearchService;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class POServiceInInventoryImpl implements POServiceInInventory {
    private final Clock clock;

    // =========== Utils ===========
    private final SecurityUtils securityUtils;

    // =========== Components ===========
    private final InventoryStatusModifier inventoryStatusModifier;
    private final ProductStatusModifier productStatusModifier;
    private final StockManagementLogic stockManagementLogic;

    // =========== Services ===========
    private final StockMovementService stockMovementService; // Used to log the stock movement
    private final InventoryQueryService inventoryQueryService;
    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final PurchaseOrderSearchService purchaseOrderSearchService;

    // =========== Repositories ===========
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPendingPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        // Delegate to query service
        return purchaseOrderQueryService.getAllPendingPurchaseOrders(page, size, sortBy, sortDirection);
    }

    // STOCK IN button logic.
    @Override
    @Transactional
    public ApiResponse<Void> receivePurchaseOrder(Long orderId, @Valid ReceiveStockRequestDto receiveRequest, String jwtToken) throws BadRequestException {
        try {
            String updatedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            if(orderId == null || orderId < 1){
                throw new IllegalArgumentException("PO (receivePurchaseOrder): Invalid order ID provided: " + orderId);
            }

            PurchaseOrder order = purchaseOrderQueryService.findById(orderId);

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

            log.info("IS (receiveIncomingStock): Updated incoming stock order successfully. PO Number: {}", order.getPONumber());
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
            productStatusModifier.updateIncomingProductStatusInPm(order.getProduct());
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
                    inventoryQueryService.getInventoryProductByProductId(order.getProduct().getProductID());
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

    @Override
    @Transactional
    public ApiResponse<Void> cancelPurchaseOrderInternal(Long orderId, String jwtToken) throws BadRequestException {
        try {
            if(orderId == null || orderId < 1){
                throw new IllegalArgumentException("PO (cancelPurchaseOrderInternal): Invalid order ID provided: " + orderId);
            }
            String user = securityUtils.validateAndExtractUsername(jwtToken);

            PurchaseOrder purchaseOrder = purchaseOrderQueryService.findById(orderId);

            // Cancellation only allowed if not already received or failed
            if (purchaseOrder.isFinalized()) {
                throw new ValidationException("PO (cancelPurchaseOrderInternal): Cannot cancel an incoming stock record with status: " + purchaseOrder.getStatus());
            }

            purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrder.setUpdatedBy(user);

            // Return back the Product Management section into the previous state
            productStatusModifier.updateIncomingProductStatusInPm(purchaseOrder.getProduct());

            // Return back the Inventory Control into the previous state
            Optional<InventoryControlData> inventoryProductOpt =
                    inventoryQueryService.getInventoryProductByProductId(purchaseOrder.getProduct().getProductID());
            inventoryProductOpt.ifPresent(inventoryStatusModifier::updateInventoryStatus);

            purchaseOrderRepository.save(purchaseOrder);
            log.info("PO (cancelPurchaseOrderInternal): SalesOrder cancelled successfully. PO Number: {}", purchaseOrder.getPONumber());
            return new ApiResponse<>(true, "The order cancelled successfully.");
        } catch (DataAccessException e) {
            log.error("PO (cancelPurchaseOrderInternal): Database error while cancelling order", e);
            throw new DatabaseException("Failed to cancel order due to database error:" + e.getMessage());
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            log.error("PO (cancelPurchaseOrderInternal): Unexpected error while cancelling order", e);
            throw new ServiceException("Failed to cancel order: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchInIncomingPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection) {
        return purchaseOrderSearchService.searchPending(text, page, size, sortBy, sortDirection);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterIncomingPurchaseOrders(PurchaseOrderStatus status, ProductCategories category,
                                                                           String sortBy, String sortDirection, int page, int size){
        return purchaseOrderSearchService.filterPending(status, category, sortBy, sortDirection, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllOverduePurchaseOrders(int page, int size) {
        // Delegate to query service
        return purchaseOrderQueryService.getAllOverduePurchaseOrders(page, size);
    }
}
