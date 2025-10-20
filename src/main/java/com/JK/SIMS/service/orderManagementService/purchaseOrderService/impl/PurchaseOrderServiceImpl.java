package com.JK.SIMS.service.orderManagementService.purchaseOrderService.impl;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.purchaseOrder.dtos.PurchaseOrderRequestDto;
import com.JK.SIMS.models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailSender;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.productManagementService.PMServiceHelper;
import com.JK.SIMS.service.utilities.purchaseOrderFilterLogic.PoFilterStrategy;
import com.JK.SIMS.service.utilities.purchaseOrderSearchLogic.PoSearchStrategy;
import com.JK.SIMS.service.supplierService.SupplierService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.ProductCategoriesConverter;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.utilities.PurchaseOrderStatusConverter;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;



@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);
    private static final int MAX_PO_GENERATION_RETRIES = 5;
    private final Clock clock;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SecurityUtils securityUtils;
    private final GlobalServiceHelper globalServiceHelper;
    private final SupplierService supplierService;
    private final EmailSender emailSender;
    private final PMServiceHelper pmServiceHelper;
    private final ConfirmationTokenService confirmationTokenService;
    private final PurchaseOrderServiceHelper purchaseOrderServiceHelper;
    private final PoSearchStrategy poSearchStrategy;
    private final PoFilterStrategy poFilterStrategy;
    private final PurchaseOrderStatusConverter purchaseOrderStatusConverter;
    private final ProductCategoriesConverter productCategoriesConverter;
    @Autowired
    public PurchaseOrderServiceImpl(Clock clock, PurchaseOrderRepository purchaseOrderRepository, SecurityUtils securityUtils, GlobalServiceHelper globalServiceHelper,
                                    SupplierService supplierService, EmailSender emailSender, PMServiceHelper pmServiceHelper,
                                    ConfirmationTokenService confirmationTokenService, PurchaseOrderServiceHelper purchaseOrderServiceHelper,
                                    @Qualifier("omPoSearchStrategy") PoSearchStrategy poSearchStrategy, PurchaseOrderStatusConverter purchaseOrderStatusConverter,
                                    @Qualifier("filterPurchaseOrders") PoFilterStrategy poFilterStrategy, ProductCategoriesConverter productCategoriesConverter) {
        this.clock = clock;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.securityUtils = securityUtils;
        this.globalServiceHelper = globalServiceHelper;
        this.supplierService = supplierService;
        this.emailSender = emailSender;
        this.pmServiceHelper = pmServiceHelper;
        this.confirmationTokenService = confirmationTokenService;
        this.purchaseOrderServiceHelper = purchaseOrderServiceHelper;
        this.poSearchStrategy = poSearchStrategy;
        this.poFilterStrategy = poFilterStrategy;
        this.purchaseOrderStatusConverter = purchaseOrderStatusConverter;
        this.productCategoriesConverter = productCategoriesConverter;
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
            logger.error("OM-PO (createPurchaseOrder): Failed to create incoming stock due to PO Number collision. Please try again. : {}", de.getMessage(), de);
            throw new DatabaseException("Failed to create incoming stock due to PO Number collision. Please try again.");
        } catch (ConstraintViolationException ve) {
            logger.error("OM-PO (createPurchaseOrder): Invalid purchase order request: {}", ve.getMessage());
            throw new ValidationException("Invalid purchase order request: " + ve.getMessage());
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            logger.error("OM-PO (createPurchaseOrder): Unexpected error while creating purchase order", e);
            throw new ServiceException("Failed to create purchase order: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            // Retrieve the data and return the paginated response
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
            logger.info("OM-PO (getAllPurchaseOrders): Returning {} paginated data", entityResponse.getContent().size());
            return purchaseOrderServiceHelper.transformToPaginatedSummaryView(entityResponse);
        } catch (DataAccessException da) {
            logger.error("OM-PO (getAllPurchaseOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error", da);
        } catch (PropertyReferenceException e) {
            logger.error("OM-PO (getAllPurchaseOrders): Invalid sort field provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid sort field provided: " + e.getMessage());
        } catch (Exception e) {
            logger.error("OM-PO (getAllPurchaseOrders): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred:", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DetailsPurchaseOrderView getDetailsForPurchaseOrderId(Long orderId) throws ResourceNotFoundException {
        try {
            purchaseOrderServiceHelper.validateOrderId(orderId);
            PurchaseOrder purchaseOrder = purchaseOrderServiceHelper.getPurchaseOrderById(orderId);
            logger.info("OM-PO (getDetailsForPurchaseOrderId): Returning details for PO ID: {}", orderId);
            return new DetailsPurchaseOrderView(purchaseOrder);
        } catch (DataAccessException da) {
            logger.error("OM-PO (getDetailsForPurchaseOrderId): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Database error", da);
        } catch (ResourceNotFoundException e) {
            logger.error("OM-PO (getDetailsForPurchaseOrderId): Order ID {} not found: {}", orderId, e.getMessage(), e);
            throw new ResourceNotFoundException("Order ID " + orderId + " not found", e);
        } catch (IllegalArgumentException e) {
            logger.error("OM-PO (getDetailsForPurchaseOrderId): Invalid order ID provided: {}", e.getMessage(), e);
            throw new ValidationException("Invalid order ID provided: " + e.getMessage());
        } catch (Exception e) {
            logger.error("OM-PO (getDetailsForPurchaseOrderId): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred:", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection) {
        globalServiceHelper.validatePaginationParameters(page, size);
        if (text == null || text.trim().isEmpty()) {
            logger.warn("PO (searchPurchaseOrders): Search text is null or empty, returning all incoming orders.");
            return getAllPurchaseOrders(page, size, sortBy, sortDirection);
        }
        Page<PurchaseOrder> purchaseOrderPage = poSearchStrategy.searchInPos(text, page, size, sortBy, sortDirection);
        return purchaseOrderServiceHelper.transformToPaginatedSummaryView(purchaseOrderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(String category, String status, String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            Sort sort = sortDirection.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            // Validate the category if provided
            ProductCategories categoryValue = null;
            if (category != null) {
                categoryValue = productCategoriesConverter.convert(category); // throws ValidationException if invalid
            }
            // Validate the status if provided
            PurchaseOrderStatus statusValue = null;
            if (status != null) {
                statusValue = purchaseOrderStatusConverter.convert(status);  // throws ValidationException if invalid
            }
            return poFilterStrategy.filterPurchaseOrders(categoryValue, statusValue, pageable);
        } catch (DataAccessException da) {
            logger.error("OM-PO (FilterPurchaseOrders): Database error occurred: {}", da.getMessage(), da);
            throw new DatabaseException("Internal error", da);
        } catch (ValidationException ve){
            logger.error("OM-PO (FilterPurchaseOrders): Invalid filter provided: {}", ve.getMessage(), ve);
            throw new ValidationException("Invalid filter provided: " + ve.getMessage());
        } catch (Exception e) {
            logger.error("OM-PO (FilterPurchaseOrders): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred:", e);
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

    private void saveAndRequestPurchaseOrder(PurchaseOrder order) {
        purchaseOrderRepository.save(order);
        ConfirmationToken confirmationToken = confirmationTokenService.createConfirmationToken(order);
        emailSender.sendPurchaseOrderRequest(order.getSupplier().getEmail(), order, confirmationToken);
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
