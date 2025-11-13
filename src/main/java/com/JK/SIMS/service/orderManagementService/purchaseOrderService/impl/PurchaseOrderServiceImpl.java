package com.JK.SIMS.service.orderManagementService.purchaseOrderService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.purchaseOrder.dtos.PurchaseOrderRequest;
import com.JK.SIMS.models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailSender;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.productManagementService.utils.queryService.ProductQueryService;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderQueryService.PurchaseOrderQueryService;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.PurchaseOrderSearchService;
import com.JK.SIMS.service.supplierService.SupplierService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;



@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    // =========== Constants ===========
    private static final int MAX_PO_GENERATION_RETRIES = 5;
    private final Clock clock;

    // =========== Helpers & Utilities ===========
    private final SecurityUtils securityUtils;

    // =========== Components ===========
    private final PurchaseOrderSearchService purchaseOrderSearchService;
    private final PurchaseOrderQueryService purchaseOrderQueryService;

    // =========== External Services ===========
    private final SupplierService supplierService;
    private final ProductQueryService productQueryService;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailSender emailSender;

    // =========== Repositories ===========
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> getAllPurchaseOrders(int page, int size, String sortBy, String sortDirection) {
        return purchaseOrderQueryService.getAllPurchaseOrders(page, size, sortBy, sortDirection);
    }


    @Override
    @Transactional(readOnly = true)
    public DetailsPurchaseOrderView getDetailsForPurchaseOrder(Long orderId) throws ResourceNotFoundException {
        return purchaseOrderQueryService.getDetailsForPurchaseOrder(orderId);
    }


    @Override
    @Transactional
    public ApiResponse<PurchaseOrderRequest> createPurchaseOrder(@Valid PurchaseOrderRequest stockRequest,
                                                                 String jwtToken) throws BadRequestException {
        try {
            String orderedPerson = securityUtils.validateAndExtractUsername(jwtToken);
            ProductsForPM orderedProduct = productQueryService.isProductFinalized(stockRequest.getProductId()); // throws ValidationException if not finalized
            PurchaseOrder order = createOrderEntity(stockRequest, orderedProduct, orderedPerson);
            saveAndRequestPurchaseOrder(order);

            log.info("OM-PO (createPurchaseOrder): Product ordered successfully. PO Number: {}", order.getPONumber());
            return new ApiResponse<>(true, "Order created successfully. PO Number: " + order.getPONumber(), stockRequest);

        } catch (DataIntegrityViolationException de) {
            log.error("OM-PO (createPurchaseOrder): Failed to create incoming stock due to PO Number collision. Please try again. : {}", de.getMessage(), de);
            throw new DatabaseException("Failed to create incoming stock due to PO Number collision. Please try again.");
        } catch (ConstraintViolationException ve) {
            log.error("OM-PO (createPurchaseOrder): Invalid purchase order request: {}", ve.getMessage());
            throw new ValidationException("Invalid purchase order request: " + ve.getMessage());
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof ValidationException || e instanceof BadRequestException) {
                throw e;
            }
            log.error("OM-PO (createPurchaseOrder): Unexpected error while creating purchase order", e);
            throw new ServiceException("Failed to create purchase order: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchPurchaseOrders(String text, int page, int size, String sortBy, String sortDirection) {
        return purchaseOrderSearchService.searchAll(text, page, size, sortBy, sortDirection);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(ProductCategories category, PurchaseOrderStatus status, String sortBy, String sortDirection, int page, int size) {
        return purchaseOrderSearchService.filterAll(category, status, sortBy, sortDirection, page, size);
    }

    private PurchaseOrder createOrderEntity(PurchaseOrderRequest stockRequest,
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

                log.warn("OM (generatePoNumber): Collision detected for potential PO Number: {}. Retrying... (Attempt {}/{})",
                        potentialPONumber, attempt + 1, MAX_PO_GENERATION_RETRIES);
            }
            throw new ServiceException("Failed to generate a unique PO Number after " + MAX_PO_GENERATION_RETRIES + " attempts");
        } catch (Exception e){
            log.error("OM (generatePoNumber): Failed to generate a unique PO Number due to: {}", e.getMessage(), e);
            throw new ServiceException("Failed to generate a unique PO Number due to: " + e.getMessage(), e);
        }
    }

}
