package com.JK.SIMS.service.purchaseOrderService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.*;
import com.JK.SIMS.models.IC_models.purchaseOrder.token.ConfirmationToken;
import com.JK.SIMS.models.IC_models.purchaseOrder.token.ConfirmationTokenStatus;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailService;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import com.JK.SIMS.service.supplier_service.SupplierService;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.JK.SIMS.service.purchaseOrderService.PurchaseOrderServiceHelper.buildConfirmationPage;

@Service
public class PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderService.class);
    private static final int MAX_PO_GENERATION_RETRIES = 5;
    private final Clock clock;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final GlobalServiceHelper globalServiceHelper;
    private final SupplierService supplierService;
    private final EmailService emailService;
    private final ProductManagementService pmService;
    private final InventoryControlService inventoryControlService;
    private final ConfirmationTokenService confirmationTokenService;
    private final PurchaseOrderServiceHelper poServiceHelper;
    @Autowired
    public PurchaseOrderService(Clock clock, PurchaseOrderRepository purchaseOrderRepository, GlobalServiceHelper globalServiceHelper,
                                SupplierService supplierService, EmailService emailService, ProductManagementService pmService,
                                @Lazy InventoryControlService inventoryControlService, ConfirmationTokenService confirmationTokenService, PurchaseOrderServiceHelper poServiceHelper) {
        this.clock = clock;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.globalServiceHelper = globalServiceHelper;
        this.supplierService = supplierService;
        this.emailService = emailService;
        this.pmService = pmService;
        this.inventoryControlService = inventoryControlService;
        this.confirmationTokenService = confirmationTokenService;
        this.poServiceHelper = poServiceHelper;
    }

    @Transactional
    public void createPurchaseOrder(@Valid PurchaseOrderRequestDto stockRequest, String jwtToken) throws BadRequestException {
        try {
            String orderedPerson = globalServiceHelper.validateAndExtractUser(jwtToken);
            ProductsForPM orderedProduct = validateAndGetProduct(stockRequest.getProductId());
            PurchaseOrder order = createOrderEntity(stockRequest, orderedProduct, orderedPerson);
            savePurchaseOrder(order);
            logger.info("IS (createPurchaseOrder): Product ordered successfully. PO Number: {}", order.getPONumber());
        } catch (DataIntegrityViolationException de) {
            throw new DatabaseException("IS (createPurchaseOrder): Failed to create incoming stock due to PO Number collision. Please try again.");
        } catch (ConstraintViolationException ve) {
            throw new ValidationException("IS (createPurchaseOrder): Invalid purchase order request: " + ve.getMessage());
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            throw new ServiceException("IS (createPurchaseOrder): Failed to create purchase order: " + e.getMessage());
        }
    }

    // STOCK IN button logic.
    @Transactional
    public ApiResponse receiveIncomingStock(Long orderId, @Valid ReceiveStockRequestDto receiveRequest, String jwtToken) throws BadRequestException {
        try {
            String updatedPerson = globalServiceHelper.validateAndExtractUser(jwtToken);
            validateOrderId(orderId); // check against null, throws an exception

            PurchaseOrder order = getIncomingStockOrderById(orderId);

            if(order.isFinalized()){
                throw new ValidationException("IS (receiveIncomingStock): Cannot receive stock for finalized order");
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

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> getAllIncomingStockRecords(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
            PaginatedResponse<PurchaseOrderResponseDto> dtoResponse = poServiceHelper.transformToPaginatedDtoResponse(entityResponse);
            logger.info("IS (getAllIncomingStock): Returning {} paginated data", dtoResponse.getContent().size());
            return dtoResponse;
        }catch (DataAccessException da){
            throw new DatabaseException("IS (getAllIncomingStock): Database error", da);
        }catch (Exception e){
            throw new ServiceException("IS (getAllIncomingStock): Service error occurred", e);
        }
    }

    @Transactional
    public ApiResponse cancelIncomingStockInternal(Long orderId, String jwtToken) throws BadRequestException {
        try {
            validateOrderId(orderId); // check against null, throws an exception
            String user = globalServiceHelper.validateAndExtractUser(jwtToken);

            PurchaseOrder purchaseOrder = getIncomingStockOrderById(orderId);

            // Cancellation only allowed if not already received or failed  
            if (purchaseOrder.isFinalized()) {
                throw new ValidationException("IS (cancelIncomingStock): Cannot cancel an incoming stock record with status: " + purchaseOrder.getStatus());
            }

            purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrder.setUpdatedBy(user);

            // Return back the Product Management section into the previous state
            updateProductManagementStatus(purchaseOrder.getProduct());

            // Return back the Inventory Control into the previous state
            Optional<InventoryData> inventoryProductOpt =
                    inventoryControlService.getInventoryProductByProductId(purchaseOrder.getProduct().getProductID());
            handleActiveStatusUpdate(inventoryProductOpt);

            purchaseOrderRepository.save(purchaseOrder);
            logger.info("IS (cancelIncomingStock): SalesOrder cancelled successfully. PO Number: {}", purchaseOrder.getPONumber());
            return new ApiResponse(true, "The order cancelled successfully.");
        } catch (DataAccessException e) {
            logger.error("IS (cancelIncomingStock): Database error while cancelling order", e);
            throw new DatabaseException("IS (cancelIncomingStock): Failed to cancel order due to database error");
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            logger.error("IS (cancelIncomingStock): Unexpected error while cancelling order", e);
            throw new ServiceException("IS (cancelIncomingStock): Failed to cancel order: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getIncomingStockOrderById(Long orderId) {
        return purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("IS (getIncomingStockOrderById): No incoming stock order found for ID: " + orderId));
    }

    // TODO: Only set to INCOMING in IC when email is Confirmed
    // Method for the Email Confirmation
    @Transactional
    public String confirmPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        ProductsForPM orderedProduct = order.getProduct();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                // Update the status of the product in the Inventory Control section
                handleInventoryStatusUpdates(orderedProduct);

                // Update the PO
                order.setStatus(PurchaseOrderStatus.DELIVERY_IN_PROCESS);
                order.setUpdatedBy("Supplier via Email Link.");
                purchaseOrderRepository.save(order);

                updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CONFIRMED);

                logger.info("IS (confirmOrder): SalesOrder confirmed by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("SalesOrder " + order.getPONumber() + " has been successfully confirmed!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when confirming order. SalesOrder ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            logger.warn("IS (confirmOrder): SalesOrder ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
            return buildConfirmationPage("SalesOrder already confirmed or cancelled.", "alert-danger");
        }
    }

    // Method for the Email Cancellation
    @Transactional
    public String cancelPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                order.setStatus(PurchaseOrderStatus.FAILED);
                order.setUpdatedBy("Supplier via Email Link.");
                purchaseOrderRepository.save(order);

                updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CANCELLED);

                // Change the status from PLANNING -> ACTIVE
                updateProductManagementStatus(order.getProduct());

                logger.info("IS (cancelPurchaseOrder): SalesOrder cancelled by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("SalesOrder " + order.getPONumber() + " has been successfully cancelled!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when cancelling order. SalesOrder ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            logger.warn("IS (cancelPurchaseOrder): SalesOrder ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
            return buildConfirmationPage("SalesOrder already confirmed or cancelled.", "alert-danger");
        }
    }

    @Transactional(readOnly = true)
    public Long getTotalValidPoSize(){
        return purchaseOrderRepository.getTotalValidPoSize();
    }

    // Private helper methods

    @Nullable
    private ConfirmationToken validateConfirmationToken(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.getConfirmationToken(token);
        if (confirmationToken.getClickedAt() != null ||
                confirmationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return confirmationToken;
    }

    private void updateConfirmationToken(ConfirmationToken token, ConfirmationTokenStatus status) {
        token.setClickedAt(GlobalServiceHelper.now(clock));
        token.setStatus(status);
        confirmationTokenService.saveConfirmationToken(token);
    }

    private ProductsForPM validateAndGetProduct(String productId) {
        ProductsForPM product = pmService.findProductById(productId);

        if (GlobalServiceHelper.amongInvalidStatus(product.getStatus())) {
            throw new ValidationException("Product is not for sale and cannot be ordered. Please update the status in the PM section first.");
        }
        return product;
    }

    private void handleInventoryStatusUpdates(ProductsForPM orderedProduct) {
        Optional<InventoryData> inventoryProductOpt =
                inventoryControlService.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, inventoryProductOpt);
        } else if (orderedProduct.getStatus() == ProductStatus.ACTIVE) {
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handlePlanningStatusUpdate(ProductsForPM orderedProduct, Optional<InventoryData> inventoryProductOpt) {
        // Update the product status from PLANNING to ON_ORDER
        orderedProduct.setStatus(ProductStatus.ON_ORDER);
        pmService.saveProduct(orderedProduct);

        if (inventoryProductOpt.isEmpty()) {
            // Product not in inventory, add it
            inventoryControlService.addProduct(orderedProduct, true);
        } else {
            // Else update the existing inventory status to INCOMING
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handleActiveStatusUpdate(Optional<InventoryData> inventoryDataOpt) {
        if(inventoryDataOpt.isPresent()) {
            InventoryData inventoryData = inventoryDataOpt.get();
            if (inventoryData.getStatus() != InventoryDataStatus.INCOMING) {
                inventoryData.setStatus(InventoryDataStatus.INCOMING);
                inventoryControlService.saveInventoryProduct(inventoryData);
            }
        }
        // Active status products will always be present in the Inventory
    }

    private PurchaseOrder createOrderEntity(PurchaseOrderRequestDto stockRequest, ProductsForPM orderedProduct, String orderedPerson) {
        Supplier supplier = supplierService.getSupplierEntityById(stockRequest.getSupplierId());
        String poNumber = generatePoNumber(supplier.getId());

        return new PurchaseOrder(
                orderedProduct,
                supplier,
                stockRequest.getOrderQuantity(),
                stockRequest.getExpectedArrivalDate(),
                stockRequest.getNotes(),
                poNumber,
                orderedPerson,
                clock
        );
    }

    private void savePurchaseOrder(PurchaseOrder order) {
        purchaseOrderRepository.save(order);
        ConfirmationToken confirmationToken = confirmationTokenService.createConfirmationToken(order);
        emailService.sendPurchaseOrderRequest(order.getSupplier().getEmail(), order, confirmationToken);
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
                throw new ValidationException("IS (updateIncomingStockOrder): Actual arrival date cannot be in the future");
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
            updateProductManagementStatus(order.getProduct());
        } else if (order.getReceivedQuantity() > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        // If no quantity received, status remains unchanged
    }

    private void updateProductManagementStatus(ProductsForPM orderedProduct) {
        if (orderedProduct.getStatus() == ProductStatus.ON_ORDER) {
            orderedProduct.setStatus(ProductStatus.ACTIVE);
            pmService.saveProduct(orderedProduct);
        }
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

    private String generatePoNumber(Long supplierId) {
        for (int attempt = 0; attempt < MAX_PO_GENERATION_RETRIES; attempt++) {
            String uniqueIdPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String potentialPONumber = "PO-" + supplierId + "-" + uniqueIdPart;

            if (!purchaseOrderRepository.existsByPONumber(potentialPONumber)) {
                return potentialPONumber;
            }

            logger.warn("IS (generatePoNumber): Collision detected for potential PO Number: {}. Retrying... (Attempt {}/{})",
                    potentialPONumber, attempt + 1, MAX_PO_GENERATION_RETRIES);
        }

        throw new ServiceException("Failed to generate a unique PO Number after " + MAX_PO_GENERATION_RETRIES + " attempts.");
    }

}
