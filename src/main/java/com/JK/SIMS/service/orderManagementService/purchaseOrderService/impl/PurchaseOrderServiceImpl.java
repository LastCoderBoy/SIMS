package com.JK.SIMS.service.orderManagementService.purchaseOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.*;
import com.JK.SIMS.models.IC_models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.IC_models.purchaseOrder.confirmationToken.ConfirmationTokenStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderRequestDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.helperServices.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.productManagementService.PMServiceHelper;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailService;
import com.JK.SIMS.service.supplierService.SupplierService;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
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
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.JK.SIMS.service.helperServices.PurchaseOrderServiceHelper.buildConfirmationPage;


@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);
    private static final int MAX_PO_GENERATION_RETRIES = 5;
    private final Clock clock;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SecurityUtils securityUtils;
    private final GlobalServiceHelper globalServiceHelper;
    private final SupplierService supplierService;
    private final EmailService emailService;
    private final PMServiceHelper pmServiceHelper;
    private final InventoryControlService inventoryControlService;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final ConfirmationTokenService confirmationTokenService;
    private final PurchaseOrderServiceHelper poServiceHelper;
    @Autowired
    public PurchaseOrderServiceImpl(Clock clock, PurchaseOrderRepository purchaseOrderRepository, SecurityUtils securityUtils, GlobalServiceHelper globalServiceHelper,
                                    SupplierService supplierService, EmailService emailService, PMServiceHelper pmServiceHelper,
                                    InventoryControlService inventoryControlService, InventoryServiceHelper inventoryServiceHelper, ConfirmationTokenService confirmationTokenService, PurchaseOrderServiceHelper poServiceHelper) {
        this.clock = clock;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.securityUtils = securityUtils;
        this.globalServiceHelper = globalServiceHelper;
        this.supplierService = supplierService;
        this.emailService = emailService;
        this.pmServiceHelper = pmServiceHelper;
        this.inventoryControlService = inventoryControlService;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.confirmationTokenService = confirmationTokenService;
        this.poServiceHelper = poServiceHelper;
    }

    @Override
    @Transactional
    public ApiResponse<PurchaseOrderRequestDto> createPurchaseOrder(@Valid PurchaseOrderRequestDto stockRequest, String jwtToken) throws BadRequestException {
        try {
            String orderedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            ProductsForPM orderedProduct = validateAndGetProduct(stockRequest.getProductId());
            PurchaseOrder order = createOrderEntity(stockRequest, orderedProduct, orderedPerson);
            saveAndRequestPurchaseOrder(order);
            logger.info("IS (createPurchaseOrder): Product ordered successfully. PO Number: {}", order.getPONumber());
            return new ApiResponse<>(true, "Order created successfully. PO Number: " + order.getPONumber(), stockRequest);
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

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            // Retrieve the data and return the paginated response
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
            logger.info("OM-PO (getAllPurchaseOrders): Returning {} paginated data", entityResponse.getContent().size());
            return poServiceHelper.transformToPaginatedDtoResponse(entityResponse);
        } catch (DataAccessException da) {
            logger.error("OM-PO (getAllPurchaseOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("OM-PO (getAllPurchaseOrders): Database error", da);
        } catch (PropertyReferenceException e) {
            logger.error("OM-PO (getAllPurchaseOrders): Invalid sort field provided: {}", e.getMessage(), e);
            throw new ValidationException("OM-PO (getAllPurchaseOrders): Invalid sort field provided: " + e.getMessage());
        }
    }

//    @Transactional(readOnly = true)
//    public PaginatedResponse<PurchaseOrderResponseDto> getAllIncomingStockRecords(int page, int size) {
//        try {
//            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
//            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
//            PaginatedResponse<PurchaseOrderResponseDto> dtoResponse = poServiceHelper.transformToPaginatedDtoResponse(entityResponse);
//            logger.info("IS (getAllIncomingStock): Returning {} paginated data", dtoResponse.getContent().size());
//            return dtoResponse;
//        }catch (DataAccessException da){
//            throw new DatabaseException("IS (getAllIncomingStock): Database error", da);
//        }catch (Exception e){
//            throw new ServiceException("IS (getAllIncomingStock): Service error occurred", e);
//        }
//    }

    // Method for the Email Confirmation
    @Transactional
    public ApiResponse<String> confirmPurchaseOrder(String token, LocalDate expectedArrivalDate) {
        ConfirmationToken confirmationToken = confirmationTokenService.validateConfirmationToken(token);
        if (confirmationToken == null) {
            return new ApiResponse<>(false, "Email link is expired or already processed.");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                // Update expected arrival date from supplier
                order.setExpectedArrivalDate(expectedArrivalDate);

                // Update inventory status if needed
                handleInventoryStatusUpdates(order.getProduct());

                // Set PO status
                order.setStatus(PurchaseOrderStatus.DELIVERY_IN_PROCESS);
                order.setUpdatedBy("Supplier via Confirmation Link");
                purchaseOrderRepository.save(order);

                // Update token
                confirmationTokenService.updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CONFIRMED);

                logger.info("OM (confirmPurchaseOrder): Order confirmed by supplier. PO Number: {}", order.getPONumber());
                return new ApiResponse<>(true, "Order " + order.getPONumber() + " confirmed successfully with expected arrival: " + expectedArrivalDate);
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when confirming order. Order ID: {}", order.getId());
                return new ApiResponse<>(false, "This order has already been processed by someone else.");
            }
        } else {
            logger.warn("OM (confirmPurchaseOrder): Order ID {} is not in AWAITING_APPROVAL status. Current status: {}", order.getId(), order.getStatus());
            return new ApiResponse<>(false, "Order already confirmed or cancelled.");
        }
    }

    // Method for the Email Cancellation
    @Transactional
    public String cancelPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                order.setStatus(PurchaseOrderStatus.FAILED);
                order.setUpdatedBy("Supplier via Email Link.");
                purchaseOrderRepository.save(order);

                confirmationTokenService.updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CANCELLED);

                // Change the status from PLANNING -> ACTIVE
//                pmService.updateIncomingProductStatusInPm(order.getProduct());

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

    // Private helper methods
    private ProductsForPM validateAndGetProduct(String productId) throws ResourceNotFoundException, ValidationException {
        ProductsForPM product = pmServiceHelper.findProductById(productId);
        if (GlobalServiceHelper.amongInvalidStatus(product.getStatus())) {
            throw new ValidationException("Product is not for sale and cannot be ordered. Please update the status in the PM section first.");
        }
        return product;
    }

    private void handleInventoryStatusUpdates(ProductsForPM orderedProduct) {
        Optional<InventoryData> inventoryProductOpt =
                inventoryServiceHelper.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, inventoryProductOpt);
        } else if (orderedProduct.getStatus() == ProductStatus.ACTIVE) {
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handlePlanningStatusUpdate(ProductsForPM orderedProduct, Optional<InventoryData> inventoryProductOpt) {
        // Update the product status from PLANNING to ON_ORDER
        orderedProduct.setStatus(ProductStatus.ON_ORDER);
        pmServiceHelper.saveProduct(orderedProduct);

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

    private PurchaseOrder createOrderEntity(PurchaseOrderRequestDto stockRequest,
                                            ProductsForPM orderedProduct, String orderedPerson) throws ResourceNotFoundException {
        Supplier supplier = supplierService.getSupplierEntityById(stockRequest.getSupplierId());
        String poNumber = generatePoNumber(supplier.getId());

        return new PurchaseOrder(
                orderedProduct,
                supplier,
                stockRequest.getOrderQuantity(),
                stockRequest.getExpectedArrivalDate(), // will be null if not provided.
                stockRequest.getNotes(),
                poNumber,
                orderedPerson,
                clock
        );
    }

    // TODO: Set the expectedArrivalDate from the Supplier side.
    private void saveAndRequestPurchaseOrder(PurchaseOrder order) {
        purchaseOrderRepository.save(order);
        ConfirmationToken confirmationToken = confirmationTokenService.createConfirmationToken(order);
        emailService.sendPurchaseOrderRequest(order.getSupplier().getEmail(), order, confirmationToken);
    }

    private String generatePoNumber(Long supplierId) {
        try{
            for (int attempt = 0; attempt < MAX_PO_GENERATION_RETRIES; attempt++) {
                String uniqueIdPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String potentialPONumber = "PO-" + supplierId + "-" + uniqueIdPart;

                if (!purchaseOrderRepository.existsByPONumber(potentialPONumber)) {
                    return potentialPONumber;
                }

                logger.warn("OM (generatePoNumber): Collision detected for potential PO Number: {}. Retrying... (Attempt {}/{})",
                        potentialPONumber, attempt + 1, MAX_PO_GENERATION_RETRIES);
            }
            throw new ServiceException("Failed to generate a unique PO Number after " + MAX_PO_GENERATION_RETRIES + " attempts");
        } catch (Exception e){
            logger.error("OM (generatePoNumber): Failed to generate a unique PO Number due to: {}", e.getMessage(), e);
            throw new ServiceException("Failed to generate a unique PO Number due to: " + e.getMessage(), e);
        }
    }

}
