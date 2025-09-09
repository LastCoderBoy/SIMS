package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryMetrics;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import com.JK.SIMS.service.salesOrderService.SalesOrderService;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.JK.SIMS.service.utilities.GlobalServiceHelper.amongInvalidStatus;

@Service
public class InventoryControlService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    private final ProductManagementService pmService;
    private final SalesOrderService salesOrderService;
    private final DamageLossService damageLossService;
    private final PurchaseOrderService purchaseOrderService;
    private final InventoryServiceHelper inventoryServiceHelper;
    @Autowired
    public InventoryControlService(IC_repository icRepository, @Lazy ProductManagementService pmService, @Lazy SalesOrderService salesOrderService,
                                   DamageLossService damageLossService, @Lazy PurchaseOrderService purchaseOrderService, InventoryServiceHelper inventoryServiceHelper) {
        this.icRepository = icRepository;
        this.pmService = pmService;
        this.salesOrderService = salesOrderService;
        this.damageLossService = damageLossService;
        this.purchaseOrderService = purchaseOrderService;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Transactional(readOnly = true)
    public InventoryPageResponse getInventoryControlPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            //TODO: Display Incoming(PO) order as well at the end of the page.

            // OUTGOING(SO) PENDING orders will be displayed at the end of the page
            PaginatedResponse<SalesOrderResponseDto> allPendingOrderDtos =
                    salesOrderService.getAllSalesOrdersSorted(page, size, "orderDate", "desc", Optional.of(SalesOrderStatus.PENDING));

            // Create the response object
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse(
                    metrics.getTotalCount(),
                    metrics.getLowStockCount(), // Checks against the VALID products only
                    purchaseOrderService.getTotalValidPoSize(),
                    salesOrderService.getTotalValidOutgoingStockSize(),
                    damageLossService.getDamageLossMetrics().getTotalReport(),
                    allPendingOrderDtos
            );
            logger.info("IC (getInventoryControlPageData): Sending page {} with {} products.", page, allPendingOrderDtos.getContent().size());
            return inventoryPageResponse;
        } catch (DataAccessException e) {
            logger.error("IC (getInventoryControlPageData): Database access error while retrieving inventory data.", e);
            throw new DatabaseException("IC (getInventoryControlPageData): Failed to retrieve products from database", e);
        } catch (Exception e) {
            logger.error("IC (getInventoryControlPageData): Unexpected error occurred while loading IC page data.", e);
            throw new ServiceException("IC (getInventoryControlPageData): Failed to retrieve products", e);
        }
    }

    @Transactional
    public void addProduct(ProductsForPM product, boolean isUnderTransfer){
        try {
            InventoryData inventoryData = new InventoryData();

            //Generating the SKU and populating the object field
            String sku = generateSKU(product.getProductID(), product.getCategory());
            inventoryData.setSKU(sku);

            // Set the basic fields
            inventoryData.setPmProduct(product);
            inventoryData.setLocation(product.getLocation());
            inventoryData.setCurrentStock(0);
            inventoryData.setMinLevel(0);

            // Handle the status properly
            if(amongInvalidStatus(product.getStatus())){
                inventoryData.setStatus(InventoryDataStatus.INVALID);
            }else {
                // isUnderTransfer means the Product is INCOMING
                if(isUnderTransfer){
                    inventoryData.setStatus(InventoryDataStatus.INCOMING);
                } else {
                    inventoryData.setStatus(InventoryDataStatus.LOW_STOCK);
                }
            }
            icRepository.save(inventoryData);
            logger.info("IC: New product is added with the {} SKU", sku);
        } catch (DataAccessException da){
            throw new DatabaseException("IC (addProduct): Failed to save inventory data due to database error", da);
        } catch (InventoryException ex){
            throw ex;
        } catch (Exception e){
            throw new ServiceException("IC: Unexpected error occurred while adding product", e);
        }
    }

    // Helper methods.
    @Transactional(readOnly = true)
    public InventoryData getInventoryDataBySku(String sku) throws BadRequestException {
        return icRepository.findBySKU(sku)
                .orElseThrow(() -> new BadRequestException(
                        "IC (updateProduct): No product with SKU " + sku + " found"));
    }

    @Transactional(readOnly = true)
    public Optional<InventoryData> getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStockLevels(InventoryData existingProduct, Optional<Integer> newStockLevel, Optional<Integer> newMinLevel ) {
        // Update current stock if provided
        newStockLevel.ifPresent(existingProduct::setCurrentStock);

        // Update minimum level if provided
        newMinLevel.ifPresent(existingProduct::setMinLevel);

        //Update the status based on the latest update
        inventoryServiceHelper.updateInventoryStatus(existingProduct);

        icRepository.save(existingProduct);
    }

    // Helper method for internal use
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveInventoryProduct(InventoryData inventoryData) {
        try {
            icRepository.save(inventoryData);
            logger.info("IC (saveInventoryProduct): Successfully saved/updated inventory data with SKU {}",
                    inventoryData.getSKU());
        } catch (DataAccessException da) {
            logger.error("IC (saveInventoryProduct): Database error while saving inventory data: {}",
                    da.getMessage());
            throw new DatabaseException("Failed to save inventory data", da);
        } catch (Exception e) {
            logger.error("IC (saveInventoryProduct): Unexpected error while saving inventory data: {}",
                    e.getMessage());
            throw new ServiceException("Failed to save inventory data", e);
        }
    }

    // Helper methods for internal use
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByProductId(String productId) {
        try {
            icRepository.deleteByProduct_ProductID(productId);
            logger.info("IC (deleteByProductId): Successfully deleted inventory data for product ID {}", productId);
        } catch (DataAccessException da) {
            logger.error("IC (deleteByProductId): Database error while deleting inventory data: {}", da.getMessage());
            throw new DatabaseException("Failed to delete inventory data", da);
        } catch (Exception e) {
            logger.error("IC (deleteByProductId): Unexpected error while deleting inventory data: {}", e.getMessage());
            throw new ServiceException("Failed to delete inventory data", e);
        }
    }

    // Reserve stock atomically - returns true if successful, false if insufficient stock
    @Transactional
    public boolean reserveStock(String productId, Integer quantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            int availableStock = inventory.getCurrentStock() - inventory.getReservedStock();

            if (availableStock >= quantity) {
                inventory.setReservedStock(inventory.getReservedStock() + quantity);
                icRepository.save(inventory);
                logger.debug("IC (reserveStock): Reserved {} units for product {}", quantity, productId);
                return true;
            }

            logger.warn("IC (reserveStock): Insufficient stock for product {}. Available: {}, Requested: {}",
                    productId, availableStock, quantity);
            return false;

        } catch (DataAccessException e) {
            logger.error("IC (reserveStock): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to reserve stock", e);
        }
    }

    @Transactional
    public void fulfillReservation(String productId, int quantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            // Deduct from both current stock and reserved stock
            inventory.setCurrentStock(inventory.getCurrentStock() - quantity);
            inventory.setReservedStock(inventory.getReservedStock() - quantity);

            // Update status based on the new stock level
            inventoryServiceHelper.updateInventoryStatus(inventory);

            icRepository.save(inventory);
            logger.debug("IC (fulfillReservation): Fulfilled reservation of {} units for product {}", quantity, productId);

        } catch (DataAccessException e) {
            logger.error("IC (fulfillReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to fulfill reservation", e);
        }
    }

    // Release reservation when the order is cancelled
    @Transactional
    public void releaseReservation(String productId, int quantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - quantity));

            icRepository.save(inventory);
            logger.debug("IC (releaseReservation): Released reservation of {} units for product {}", quantity, productId);

        } catch (DataAccessException e) {
            logger.error("IC (releaseReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to release reservation", e);
        }
    }

    @Transactional
    public void updateInventoryStatus(Optional<InventoryData> productInIcOpt, InventoryDataStatus status) {
        if (productInIcOpt.isPresent()) {
            InventoryData product = productInIcOpt.get();
            product.setStatus(status);
            try {
                icRepository.save(product);
            } catch (Exception e) {
                logger.error("IC(updateInventoryStatus): Failed to update inventory data status: {}", String.valueOf(e));
                throw new ServiceException("IC(updateInventoryStatus): Error updating inventory status", e);
            }
        }
    }


    private String generateSKU(String productID, ProductCategories category){
        try {
            if (productID == null || productID.length() < 4) {
                throw new IllegalArgumentException("Product ID must be at least 4 characters (e.g., PRD001)");
            }
            if (category == null) {
                throw new IllegalArgumentException("Category cannot be null");
            }

            String lastDigits = productID.substring(3);
            String categoryStart = category.toString().substring(0, 3); // EDUCATION -> "EDU"
            return categoryStart + "-" + lastDigits;
        }
        catch (IllegalArgumentException iae) {
            logger.error("IC: Invalid input for SKU generation - productID: {}, category: {}: {}",
                    productID, category, iae.getMessage());
            throw new InventoryException("IC: Invalid product ID or category for SKU generation", iae);
        }
        catch (StringIndexOutOfBoundsException sioobe) {
            logger.error("IC: SKU generation failed due to malformed productID {} or category {}: {}",
                    productID, category, sioobe.getMessage());
            throw new InventoryException("IC: Malformed product ID or category for SKU generation", sioobe);
        }
    }

    // Get available stock (current - reserved)
    public int getAvailableStock(String productId) {
        Optional<InventoryData> inventory = icRepository.findByPmProduct_ProductID(productId);
        if (inventory.isEmpty()) {
            return 0;
        }
        return inventory.get().getCurrentStock() - inventory.get().getReservedStock();
    }
}
