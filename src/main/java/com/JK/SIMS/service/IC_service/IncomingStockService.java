package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockRequest;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockStatus;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.repository.IC_repo.IncomingStock_repository;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import com.JK.SIMS.service.GlobalServiceHelper;
import com.JK.SIMS.service.PM_service.ProductManagementService;
import com.JK.SIMS.service.UM_service.JWTService;
import com.JK.SIMS.service.email_service.EmailService;
import com.JK.SIMS.service.supplier_service.SupplierService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class IncomingStockService {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockService.class);

    private final SupplierService supplierService;
    private final EmailService emailService;
    private final Clock clock;
    private final ProductManagementService pm_service;
    private final IncomingStock_repository incomingStock_repository;
    private final JWTService jWTService;
    private final PM_repository pM_repository;
    private final InventoryControlService inventoryControlService;

    @Autowired
    public IncomingStockService(SupplierService supplierService, EmailService emailService, Clock clock, ProductManagementService pmService, IncomingStock_repository incomingStockRepository, JWTService jWTService, PM_repository pM_repository, InventoryControlService inventoryControlService) {
        this.supplierService = supplierService;
        this.emailService = emailService;
        this.clock = clock;
        pm_service = pmService;
        this.incomingStock_repository = incomingStockRepository;
        this.jWTService = jWTService;
        this.pM_repository = pM_repository;
        this.inventoryControlService = inventoryControlService;
    }

    @Transactional
    public void createPurchaseOrder(@Valid IncomingStockRequest stockRequest, String jwtToken) {
        try {
            // We don't have to validate the stockRequest as it is validated by the incomingStockRequest object

            // Validate the Token and extract the User
            String orderedPerson = jWTService.extractUsername(jwtToken);
            if (orderedPerson == null || orderedPerson.isEmpty()) {
                throw new BadRequestException("Invalid JWT token: Cannot determine ordering user.");
            }

            // Helper method will throw an exception if the product is not found.
            ProductsForPM orderedProduct = pm_service.findProductById(stockRequest.getProductId());

            if (GlobalServiceHelper.amongInvalidStatus(orderedProduct.getStatus())) {
                throw new ValidationException("Following product is not for sale and cannot be ordered, " +
                        "please update the status in the PM section first.");
            }

            // If the product status was on PLANNING, we have to the IC as well if not present.
            if(orderedProduct.getStatus() == ProductStatus.PLANNING){
                // Change from PLANNING -> ON_ORDER
                orderedProduct.setStatus(ProductStatus.ON_ORDER);

                // It might be present if the product before updated to ACTIVE -> PLANNING
                boolean isProductPresent = inventoryControlService.isInventoryProductExists(orderedProduct.getProductID());
                if(!isProductPresent) {
                    inventoryControlService.addProduct(orderedProduct, true);
                }

                pM_repository.save(orderedProduct);
            }

            // Creating the Entity object
            IncomingStock order = new IncomingStock();

            // Validate the provided supplier and set the supplier field
            Supplier supplier = supplierService.getSupplierEntityById(stockRequest.getSupplierId());
            order.setSupplier(supplier);

            // Set basic fields
            order.setOrderedQuantity(stockRequest.getOrderQuantity());
            order.setProduct(orderedProduct);
            order.setExpectedArrivalDate(stockRequest.getExpectedArrivalDate());
            order.setNotes(stockRequest.getNotes() != null ? stockRequest.getNotes() : "");

            // Generate the PO number and save it to the database 
            order.setPONumber(generatePoNumber(supplier.getId()));

            // Set default values explicitly
            order.setOrderDate(LocalDate.from(GlobalServiceHelper.now(clock)));
            order.setStatus(IncomingStockStatus.PENDING);
            order.setLastUpdated(GlobalServiceHelper.now(clock));
            order.setUpdatedBy(orderedPerson);

            incomingStock_repository.save(order);

            // Send email to supplier's email address
            emailService.sendOrderRequest(supplier.getEmail(), order);

            logger.info("IS (createPurchaseOrder): Product ordered successfully. PO Number: {}", order.getPONumber());

        }catch (EntityNotFoundException e){
            throw new EntityNotFoundException("IS (createPurchaseOrder): " + e.getMessage());
        } catch (DataIntegrityViolationException de) {
            throw new DatabaseException("IS (createPurchaseOrder): Failed to create incoming stock due to PO Number collision. Please try again.");
        } catch (ConstraintViolationException ve) {
            throw new ValidationException("IS (createPurchaseOrder): Invalid purchase order request: " + ve.getMessage());
        } catch (Exception e) {
            throw new ServiceException("IS (createPurchaseOrder): Failed to create purchase order: " + e.getMessage());
        }
    }

    private String generatePoNumber(Long supplierId) throws RuntimeException {
        String generatedPONumber = null;
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            String uniqueIdPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String potentialPONumber = "PO-" + supplierId + "-" + uniqueIdPart;

            if (!incomingStock_repository.existsByPONumber(potentialPONumber)) {
                generatedPONumber = potentialPONumber;
                break; // Found a unique one, break loop
            }
            logger.warn("IS (generatePoNumber): Collision detected for potential PO Number: {}. Retrying...", potentialPONumber);
        }

        if (generatedPONumber == null) {
            throw new RuntimeException("Failed to generate a unique PO Number after " + maxRetries + " attempts.");
        }
        return generatedPONumber;
    }
}
