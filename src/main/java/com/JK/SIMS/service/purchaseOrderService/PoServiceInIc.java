package com.JK.SIMS.service.purchaseOrderService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.ReceiveStockRequestDto;
import com.JK.SIMS.repository.PO_repo.purchaseOrderSpec.PurchaseOrderSpecification;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.InventoryControl_service.InventoryServiceHelper;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import com.JK.SIMS.service.purchaseOrderService.searchLogic.PoStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;


@Service
public class PoServiceInIc {
    private static final Logger logger = LoggerFactory.getLogger(PoServiceInIc.class);
    private final Clock clock;
    private final PurchaseOrderRepository purchaseOrderRepository;

    private final PurchaseOrderServiceHelper poServiceHelper;
    private final PoStrategy poStrategy;
    private final GlobalServiceHelper globalServiceHelper;
    private final InventoryControlService inventoryControlService;
    private final ProductManagementService pmService;
    private final InventoryServiceHelper inventoryServiceHelper;

    public PoServiceInIc(Clock clock, PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderServiceHelper poServiceHelper,
                         @Qualifier("icPoSearchStrategy") PoStrategy poStrategy, GlobalServiceHelper globalServiceHelper, InventoryControlService inventoryControlService, ProductManagementService pmService, InventoryServiceHelper inventoryServiceHelper) {
        this.clock = clock;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poServiceHelper = poServiceHelper;
        this.poStrategy = poStrategy;
        this.globalServiceHelper = globalServiceHelper;
        this.inventoryControlService = inventoryControlService;
        this.pmService = pmService;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> getAllPendingStockRecords(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAllPendingProducts(pageable);
            PaginatedResponse<PurchaseOrderResponseDto> dtoResponse =
                    poServiceHelper.transformToPaginatedDtoResponse(entityResponse);
            logger.info("PO (getAllPendingStockRecords): Returning {} paginated data", dtoResponse.getContent().size());
            return dtoResponse;
        }catch (DataAccessException da){
            throw new DatabaseException("PO (getAllPendingStockRecords): Database error", da);
        }catch (Exception e){
            logger.error("PO (getAllPendingStockRecords): Service error occurred: {}", e.getMessage(), e);
            throw new ServiceException("PO (getAllPendingStockRecords): Service error occurred", e);
        }
    }

    // STOCK IN button logic.
    @Transactional
    public ApiResponse receivePurchaseOrder(Long orderId, @Valid ReceiveStockRequestDto receiveRequest, String jwtToken) throws BadRequestException {
        try {
            String updatedPerson = globalServiceHelper.validateAndExtractUser(jwtToken);
            validateOrderId(orderId); // check against null, throws an exception

            PurchaseOrder order = poServiceHelper.getPurchaseOrderById(orderId);

            if(order.isFinalized()){
                throw new ValidationException("PO (receivePurchaseOrder): Cannot receive stock for finalized order");
            }

            updateOrderWithReceivedStock(order, receiveRequest);
            updateInventoryLevels(order, receiveRequest.getReceivedQuantity());
            finalizeOrderUpdate(order, updatedPerson);

            logger.info("IS (receiveIncomingStock): Updated incoming stock order successfully. PO Number: {}", order.getPONumber());
            return new ApiResponse(true, "Incoming stock order updated successfully.");

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

    @Transactional
    public ApiResponse cancelPurchaseOrderInternal(Long orderId, String jwtToken) throws BadRequestException {
        try {
            validateOrderId(orderId); // check against null, throws an exception
            String user = globalServiceHelper.validateAndExtractUser(jwtToken);

            PurchaseOrder purchaseOrder = poServiceHelper.getPurchaseOrderById(orderId);

            // Cancellation only allowed if not already received or failed
            if (purchaseOrder.isFinalized()) {
                throw new ValidationException("PO (cancelPurchaseOrderInternal): Cannot cancel an incoming stock record with status: " + purchaseOrder.getStatus());
            }

            purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrder.setUpdatedBy(user);

            // Return back the Product Management section into the previous state
            pmService.updateIncomingProductStatusInPm(purchaseOrder.getProduct());

            // Return back the Inventory Control into the previous state
            Optional<InventoryData> inventoryProductOpt =
                    inventoryControlService.getInventoryProductByProductId(purchaseOrder.getProduct().getProductID());
            inventoryProductOpt.ifPresent(InventoryServiceHelper::updateInventoryStatus);

            purchaseOrderRepository.save(purchaseOrder);
            logger.info("PO (cancelPurchaseOrderInternal): SalesOrder cancelled successfully. PO Number: {}", purchaseOrder.getPONumber());
            return new ApiResponse(true, "The order cancelled successfully.");
        } catch (DataAccessException e) {
            logger.error("PO (cancelPurchaseOrderInternal): Database error while cancelling order", e);
            throw new DatabaseException("PO (cancelPurchaseOrderInternal): Failed to cancel order due to database error");
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            logger.error("PO (cancelPurchaseOrderInternal): Unexpected error while cancelling order", e);
            throw new ServiceException("PO (cancelPurchaseOrderInternal): Failed to cancel order: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> searchInPendingProduct(String text, int page, int size) {
        return poStrategy.searchInPos(text, page, size);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> filterPendingPurchaseOrders(PurchaseOrderStatus status, ProductCategories category,
                                                                           String sortBy, String sortDirection, int page, int size){
        // Parse sort direction
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        // Create sort
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<PurchaseOrder> spec = Specification.where(PurchaseOrderSpecification.isPending());

        if (status != null) {
            spec = spec.and(PurchaseOrderSpecification.hasStatus(status));
        }
        if (category != null) {
            spec = spec.and(PurchaseOrderSpecification.hasProductCategory(category));
        }
        Page<PurchaseOrder> filterResult = purchaseOrderRepository.findAll(spec, pageable);
        return poServiceHelper.transformToPaginatedDtoResponse(filterResult);
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("IS (validateOrderId): SalesOrder ID cannot be null");
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

    private void updateInventoryLevels(PurchaseOrder order, int receivedQuantity) {
        if (receivedQuantity <= 0) {
            return; // No inventory update needed
        }
        try {
            Optional<InventoryData> inventoryProductOpt =
                    inventoryControlService.getInventoryProductByProductId(order.getProduct().getProductID());
            if(inventoryProductOpt.isPresent()){
                InventoryData inventoryProduct = inventoryProductOpt.get();
                int newStockLevel = inventoryProduct.getCurrentStock() + receivedQuantity;

                // The service method to update stock levels with proper error handling
                inventoryControlService.updateStockLevels(inventoryProduct,
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

    private void updateOrderStatus(PurchaseOrder order) {
        if (order.getReceivedQuantity() >= order.getOrderedQuantity()) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
            pmService.updateIncomingProductStatusInPm(order.getProduct());
        } else if (order.getReceivedQuantity() > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        // If no quantity received, status remains unchanged
    }
}
