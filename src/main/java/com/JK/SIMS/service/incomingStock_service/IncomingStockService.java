package com.JK.SIMS.service.incomingStock_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.incoming.*;
import com.JK.SIMS.models.IC_models.incoming.token.ConfirmationToken;
import com.JK.SIMS.models.IC_models.incoming.token.ConfirmationTokenStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.IC_repo.IncomingStock_repository;
import com.JK.SIMS.service.GlobalServiceHelper;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailService;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import com.JK.SIMS.service.supplier_service.SupplierService;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.JK.SIMS.service.incomingStock_service.IncomingStockHelper.buildConfirmationPage;

@Service
public class IncomingStockService {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockService.class);
    private static final int MAX_PO_GENERATION_RETRIES = 5;
    private final Clock clock;

    private final IncomingStock_repository incomingStockRepository;

    private final GlobalServiceHelper globalServiceHelper;
    private final SupplierService supplierService;
    private final EmailService emailService;
    private final ProductManagementService pmService;
    private final InventoryControlService inventoryControlService;
    private final ConfirmationTokenService confirmationTokenService;
    @Autowired
    public IncomingStockService(Clock clock, IncomingStock_repository incomingStockRepository, GlobalServiceHelper globalServiceHelper, SupplierService supplierService, EmailService emailService, ProductManagementService pmService, InventoryControlService inventoryControlService, ConfirmationTokenService confirmationTokenService) {
        this.clock = clock;
        this.incomingStockRepository = incomingStockRepository;
        this.globalServiceHelper = globalServiceHelper;
        this.supplierService = supplierService;
        this.emailService = emailService;
        this.pmService = pmService;
        this.inventoryControlService = inventoryControlService;
        this.confirmationTokenService = confirmationTokenService;
    }

    @Transactional
    public void createPurchaseOrder(@Valid IncomingStockRequestDto stockRequest, String jwtToken) throws BadRequestException {
        try {
            String orderedPerson = globalServiceHelper.validateAndExtractUser(jwtToken);
            ProductsForPM orderedProduct = validateAndGetProduct(stockRequest.getProductId());
            handleInventoryStatusUpdates(orderedProduct);
            IncomingStock order = createOrderEntity(stockRequest, orderedProduct, orderedPerson);
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

            IncomingStock order = getIncomingStockOrderById(orderId);

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
    public PaginatedResponse<IncomingStockResponseDto> getAllIncomingStockRecords(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<IncomingStock> entityResponse = incomingStockRepository.findAll(pageable);
            PaginatedResponse<IncomingStockResponseDto> dtoResponse = transformToPaginatedDto(entityResponse);
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

            IncomingStock incomingStock = getIncomingStockOrderById(orderId);

            // Cancellation only allowed if not already received or failed  
            if (incomingStock.isFinalized()) {
                throw new ValidationException("IS (cancelIncomingStock): Cannot cancel an incoming stock record with status: " + incomingStock.getStatus());
            }

            incomingStock.setStatus(IncomingStockStatus.CANCELLED);
            incomingStock.setUpdatedBy(user);

            // Return back the Product Management section into the previous state
            updateProductManagementStatus(incomingStock.getProduct());

            // Return back the Inventory Control into the previous state
            InventoryData inventoryProductOpt =
                    inventoryControlService.getInventoryProductByProductId(incomingStock.getProduct().getProductID());
            handleActiveStatusUpdate(inventoryProductOpt);

            incomingStockRepository.save(incomingStock);
            logger.info("IS (cancelIncomingStock): Order cancelled successfully. PO Number: {}", incomingStock.getPONumber());
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
    public IncomingStock getIncomingStockOrderById(Long orderId) {
        return incomingStockRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("IS (getIncomingStockOrderById): No incoming stock order found for ID: " + orderId));
    }


    @Transactional(readOnly = true)
    public PaginatedResponse<IncomingStockResponseDto> searchProduct(String text, int page, int size) {
        if (text == null || text.isEmpty()) {
            logger.warn("IS (searchProduct): Search text is null or empty");
            throw new ValidationException("IS (searchProduct): Search text cannot be empty");
        }
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<IncomingStock> searchEntityResponse = incomingStockRepository.searchProducts(text.trim().toLowerCase(), pageable);
            return transformToPaginatedDto(searchEntityResponse);
        } catch (DataAccessException dae) {
            logger.error("IS (searchProduct): Database error while searching products", dae);
            throw new DatabaseException("IS (searchProduct): Error occurred while searching products");
        } catch (Exception e) {
            logger.error("IS (searchProduct): Unexpected error while searching products", e);
            throw new ServiceException("IS (searchProduct): Error occurred while searching products");
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<IncomingStockResponseDto> filterIncomingStock(IncomingStockStatus status, ProductCategories category, int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdated"));

        Specification<IncomingStock> spec = Specification
                .where(IncomingStockSpecification.hasStatus(status))
                .and(IncomingStockSpecification.hasProductCategory(category));

        Page<IncomingStock> filterResult = incomingStockRepository.findAll(spec, pageable);
        return transformToPaginatedDto(filterResult);
    }

    // Method for the Email Confirmation
    @Transactional
    public String confirmPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        IncomingStock order = confirmationToken.getOrder();
        if (order.getStatus() == IncomingStockStatus.AWAITING_APPROVAL) {
            try {
                order.setStatus(IncomingStockStatus.DELIVERY_IN_PROCESS);
                order.setUpdatedBy("Supplier via Email Link.");
                incomingStockRepository.save(order);

                updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CONFIRMED);

                logger.info("IS (confirmOrder): Order confirmed by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("Order " + order.getPONumber() + " has been successfully confirmed!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when confirming order. Order ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            logger.warn("IS (confirmOrder): Order ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
            return buildConfirmationPage("Order already confirmed or cancelled.", "alert-danger");
        }
    }

    // Method for the Email Cancellation
    @Transactional
    public String cancelPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        IncomingStock order = confirmationToken.getOrder();
        if (order.getStatus() == IncomingStockStatus.AWAITING_APPROVAL) {
            try {
                order.setStatus(IncomingStockStatus.FAILED);
                order.setUpdatedBy("Supplier via Email Link.");
                incomingStockRepository.save(order);

                updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CANCELLED);

                // Change the status from PLANNING -> ACTIVE
                updateProductManagementStatus(order.getProduct());

                logger.info("IS (cancelPurchaseOrder): Order cancelled by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("Order " + order.getPONumber() + " has been successfully cancelled!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when cancelling order. Order ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            logger.warn("IS (cancelPurchaseOrder): Order ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
            return buildConfirmationPage("Order already confirmed or cancelled.", "alert-danger");
        }
    }

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

    // Private helper methods

    private ProductsForPM validateAndGetProduct(String productId) {
        ProductsForPM product = pmService.findProductById(productId);

        if (GlobalServiceHelper.amongInvalidStatus(product.getStatus())) {
            throw new ValidationException("Product is not for sale and cannot be ordered. Please update the status in the PM section first.");
        }

        return product;
    }

    private void handleInventoryStatusUpdates(ProductsForPM orderedProduct) {
        InventoryData inventoryProductOpt =
                inventoryControlService.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, Optional.ofNullable(inventoryProductOpt));
        } else if (orderedProduct.getStatus() == ProductStatus.ACTIVE) {
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handlePlanningStatusUpdate(ProductsForPM orderedProduct, Optional<InventoryData> inventoryProductOpt) {
        // Update product status from PLANNING to ON_ORDER
        orderedProduct.setStatus(ProductStatus.ON_ORDER);
        pmService.saveProduct(orderedProduct);

        if (inventoryProductOpt.isEmpty()) {
            // Product not in inventory, add it
            inventoryControlService.addProduct(orderedProduct, true);
        } else {
            // Update existing inventory status to INCOMING
            InventoryData inventoryData = inventoryProductOpt.get();
            if (inventoryData.getStatus() != InventoryDataStatus.INCOMING) {
                inventoryData.setStatus(InventoryDataStatus.INCOMING);
                inventoryControlService.saveInventoryProduct(inventoryData);
            }
        }
    }

    private void handleActiveStatusUpdate(InventoryData inventoryData) {
        if (inventoryData.getStatus() != InventoryDataStatus.INCOMING) {
            inventoryData.setStatus(InventoryDataStatus.INCOMING);
            inventoryControlService.saveInventoryProduct(inventoryData);
        }
        // Active status products will always be present in the Inventory
    }

    private IncomingStock createOrderEntity(IncomingStockRequestDto stockRequest, ProductsForPM orderedProduct, String orderedPerson) {
        Supplier supplier = supplierService.getSupplierEntityById(stockRequest.getSupplierId());
        String poNumber = generatePoNumber(supplier.getId());

        return new IncomingStock(
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

    private void savePurchaseOrder(IncomingStock order) {
        incomingStockRepository.save(order);
        ConfirmationToken confirmationToken = confirmationTokenService.createConfirmationToken(order);
        // emailService.sendOrderRequest(order.getSupplier().getEmail(), order, confirmationToken);
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("IS (validateOrderId): Order ID cannot be null");
        }
    }

    private void updateOrderWithReceivedStock(IncomingStock order, ReceiveStockRequestDto receiveRequest) {
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

    private void updateOrderStatus(IncomingStock order) {
        if (order.getReceivedQuantity() >= order.getOrderedQuantity()) {
            order.setStatus(IncomingStockStatus.RECEIVED);
            updateProductManagementStatus(order.getProduct());
        } else if (order.getReceivedQuantity() > 0) {
            order.setStatus(IncomingStockStatus.PARTIALLY_RECEIVED);
        }
        // If no quantity received, status remains unchanged
    }

    private void updateProductManagementStatus(ProductsForPM orderedProduct) {
        if (orderedProduct.getStatus() == ProductStatus.ON_ORDER) {
            orderedProduct.setStatus(ProductStatus.ACTIVE);
            pmService.saveProduct(orderedProduct);
        }
    }

    private void updateInventoryLevels(IncomingStock order, int receivedQuantity) {
        if (receivedQuantity <= 0) {
            return; // No inventory update needed
        }

        try {
            InventoryData inventoryProduct =
                    inventoryControlService.getInventoryProductByProductId(order.getProduct().getProductID());
            int newStockLevel = inventoryProduct.getCurrentStock() + receivedQuantity;

            // The service method to update stock levels with proper error handling
            inventoryControlService.updateStockLevels(inventoryProduct,
                    Optional.of(newStockLevel),
                    Optional.empty());
        } catch (Exception e) {
            throw new ServiceException("IS (updateIncomingStockOrder): Failed to update inventory levels: " + e.getMessage());
        }
    }

    private void finalizeOrderUpdate(IncomingStock order, String updatedPerson) {
        order.setUpdatedBy(updatedPerson);
        incomingStockRepository.save(order);
    }

    private String generatePoNumber(Long supplierId) {
        for (int attempt = 0; attempt < MAX_PO_GENERATION_RETRIES; attempt++) {
            String uniqueIdPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String potentialPONumber = "PO-" + supplierId + "-" + uniqueIdPart;

            if (!incomingStockRepository.existsByPONumber(potentialPONumber)) {
                return potentialPONumber;
            }

            logger.warn("IS (generatePoNumber): Collision detected for potential PO Number: {}. Retrying... (Attempt {}/{})",
                    potentialPONumber, attempt + 1, MAX_PO_GENERATION_RETRIES);
        }

        throw new ServiceException("Failed to generate a unique PO Number after " + MAX_PO_GENERATION_RETRIES + " attempts.");
    }

    private PaginatedResponse<IncomingStockResponseDto> transformToPaginatedDto(Page<IncomingStock> entityResponse){
        PaginatedResponse<IncomingStockResponseDto> response = new PaginatedResponse<>();
        List<IncomingStockResponseDto> convertedContent =
                entityResponse.getContent().stream().map(this::convertToDTO).toList();
        response.setContent(convertedContent);
        response.setTotalPages(entityResponse.getTotalPages());
        response.setTotalElements(entityResponse.getTotalElements());
        return response;
    }

    private IncomingStockResponseDto convertToDTO(IncomingStock order){
        return new IncomingStockResponseDto(order);
    }
}
