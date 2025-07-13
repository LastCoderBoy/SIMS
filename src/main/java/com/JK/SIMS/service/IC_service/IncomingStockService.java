package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.incoming.*;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.IC_repo.IncomingStock_repository;
import com.JK.SIMS.service.GlobalServiceHelper;
import com.JK.SIMS.service.PM_service.ProductManagementService;
import com.JK.SIMS.service.UM_service.JWTService;
import com.JK.SIMS.service.email_service.EmailService;
import com.JK.SIMS.service.supplier_service.SupplierService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncomingStockService {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockService.class);
    private static final int MAX_PO_GENERATION_RETRIES = 5;

    private final SupplierService supplierService;
    private final EmailService emailService;
    private final Clock clock;
    private final ProductManagementService pmService;
    private final IncomingStock_repository incomingStockRepository;
    private final JWTService jWTService;
    private final InventoryControlService inventoryControlService;

    @Autowired
    public IncomingStockService(SupplierService supplierService, EmailService emailService, Clock clock, ProductManagementService pmService, IncomingStock_repository incomingStockRepository, JWTService jWTService, InventoryControlService inventoryControlService) {
        this.supplierService = supplierService;
        this.emailService = emailService;
        this.clock = clock;
        this.pmService = pmService;
        this.incomingStockRepository = incomingStockRepository;
        this.jWTService = jWTService;
        this.inventoryControlService = inventoryControlService;
    }

    @Transactional
    public void createPurchaseOrder(@Valid IncomingStockRequest stockRequest, String jwtToken) throws BadRequestException {
        try {
            String orderedPerson = validateAndExtractUser(jwtToken);
            ProductsForPM orderedProduct = validateAndGetProduct(stockRequest.getProductId());
            handleInventoryStatusUpdates(orderedProduct);
            IncomingStock order = createOrderEntity(stockRequest, orderedProduct, orderedPerson);
            saveOrder(order);

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

    @Transactional
    public ApiResponse receiveIncomingStock(Long orderId, @Valid ReceiveStockRequest receiveRequest, String jwtToken) throws BadRequestException {
        try {
            String updatedPerson = validateAndExtractUser(jwtToken);
            validateOrderId(orderId);

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
            throw new ValidationException("IS (updateIncomingStockOrder): Invalid order ID format");
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("IS (updateIncomingStockOrder): " + e.getMessage());
        } catch (IllegalArgumentException | ValidationException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("IS (updateIncomingStockOrder): Unexpected error occurred: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public IncomingStock getIncomingStockOrderById(Long orderId) {
        return incomingStockRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("IS (getIncomingStockOrderById): No incoming stock order found for ID: " + orderId));
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
        Optional<InventoryData> inventoryProductOpt =
                inventoryControlService.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, inventoryProductOpt);
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
                inventoryData.setLastUpdate(GlobalServiceHelper.now(clock).truncatedTo(ChronoUnit.SECONDS));
                inventoryControlService.saveInventoryProduct(inventoryData);
            }
        }
    }

    private void handleActiveStatusUpdate(Optional<InventoryData> inventoryProductOpt) {
        if (inventoryProductOpt.isPresent()) {
            InventoryData inventoryData = inventoryProductOpt.get();
            if (inventoryData.getStatus() != InventoryDataStatus.INCOMING) {
                inventoryData.setStatus(InventoryDataStatus.INCOMING);
                inventoryData.setLastUpdate(GlobalServiceHelper.now(clock).truncatedTo(ChronoUnit.SECONDS));
                inventoryControlService.saveInventoryProduct(inventoryData);
            }
        }
    }

    private IncomingStock createOrderEntity(IncomingStockRequest stockRequest, ProductsForPM orderedProduct, String orderedPerson) {
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

    private void saveOrder(IncomingStock order) {
        incomingStockRepository.save(order);
        //emailService.sendOrderRequest(order.getSupplier().getEmail(), order);
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("IS (receiveIncomingStock): Order ID cannot be null");
        }
    }

    private void updateOrderWithReceivedStock(IncomingStock order, ReceiveStockRequest receiveRequest) {
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

        Optional<InventoryData> inventoryProduct =
                inventoryControlService.getInventoryProductByProductId(order.getProduct().getProductID());
        if (inventoryProduct.isPresent()) {
            try {
                int newStockLevel = inventoryProduct.get().getCurrentStock() + receivedQuantity;

                // The service method to update stock levels with proper error handling
                inventoryControlService.updateStockLevels(inventoryProduct.get(),
                        Optional.of(newStockLevel),
                        Optional.empty());
            } catch (Exception e) {
                throw new ServiceException("IS (updateIncomingStockOrder): Failed to update inventory levels: " + e.getMessage());
            }
        }else {
            // This should not happen in normal flow
            logger.error("IS (updateIncomingStockOrder): Product {} (ID: {}) found in IncomingStock but not in InventoryData. Inventory levels not updated.",
                    order.getProduct().getName(), order.getProduct().getProductID());
            throw new ServiceException("Inventory data not found for product " + order.getProduct().getName() + ". Please check inventory consistency.");
        }
    }

    private void finalizeOrderUpdate(IncomingStock order, String updatedPerson) {
        order.setLastUpdated(GlobalServiceHelper.now(clock));
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

    @Transactional(readOnly = true)
    public PaginatedResponse<IncomingStockResponse> getAllIncomingStockRecords(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<IncomingStock> entityResponse = incomingStockRepository.findAll(pageable);
            PaginatedResponse<IncomingStockResponse> dtoResponse = transformToPaginatedDto(entityResponse);
            logger.info("IS (getAllIncomingStock): Returning {} paginated data", dtoResponse.getContent().size());
            return dtoResponse;
        }catch (DataAccessException da){
            throw new DatabaseException("IS (getAllIncomingStock): Database error", da);
        }catch (Exception e){
            throw new ServiceException("IS (getAllIncomingStock): Service error occurred", e);
        }
    }

    @Transactional
    public ApiResponse cancelIncomingStockInternal(Long id, String jwtToken) throws BadRequestException {
        IncomingStock incomingStock = incomingStockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incoming Stock record not found with ID: " + id));

        String user = validateAndExtractUser(jwtToken);

        // Cancellation only allowed if not already received or failed
        if (incomingStock.isFinalized()) {
            throw new BadRequestException("Cannot cancel an incoming stock record with status: " + incomingStock.getStatus());
        }

        incomingStock.setStatus(IncomingStockStatus.CANCELLED);
        incomingStock.setLastUpdated(GlobalServiceHelper.now(clock));
        incomingStock.setUpdatedBy(user);

        incomingStockRepository.save(incomingStock);
        return new ApiResponse(true, "The order cancelled successfully.");
    }

    private String validateAndExtractUser(String jwtToken) throws BadRequestException {
        String username = jWTService.extractUsername(jwtToken);
        if (username == null || username.isEmpty()) {
            throw new BadRequestException("Invalid JWT token: Cannot determine user.");
        }
        return username;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<IncomingStockResponse> searchProduct(String text, int page, int size) {
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
    public PaginatedResponse<IncomingStockResponse> filterIncomingStock(IncomingStockStatus status, ProductCategories category, int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdated"));

        Specification<IncomingStock> spec = Specification
                .where(IncomingStockSpecification.hasStatus(status))
                .and(IncomingStockSpecification.hasProductCategory(category));

        Page<IncomingStock> filterResult = incomingStockRepository.findAll(spec, pageable);
        return transformToPaginatedDto(filterResult);
    }


    private PaginatedResponse<IncomingStockResponse> transformToPaginatedDto(Page<IncomingStock> entityResponse){
        PaginatedResponse<IncomingStockResponse> response = new PaginatedResponse<>();
        List<IncomingStockResponse> convertedContent = entityResponse.getContent().stream().map(this::convertToDTO).toList();
        response.setContent(convertedContent);
        response.setTotalPages(entityResponse.getTotalPages());
        response.setTotalElements(entityResponse.getTotalElements());
        return response;
    }

    private IncomingStockResponse convertToDTO(IncomingStock order){
        return new IncomingStockResponse(
                order.getId(),
                order.getPONumber(),
                order.getStatus(),
                order.getOrderDate(),
                order.getExpectedArrivalDate(),
                order.getActualArrivalDate(),
                order.getOrderedQuantity(),
                order.getReceivedQuantity(),
                order.getProduct() != null ? order.getProduct().getName() : "N/A",
                order.getProduct() != null && order.getProduct().getCategory() != null ? order.getProduct().getCategory() : null,
                order.getSupplier() != null ? order.getSupplier().getName() : "N/A",
                order.getOrderedBy(),
                order.getUpdatedBy()
        );
    }
}
