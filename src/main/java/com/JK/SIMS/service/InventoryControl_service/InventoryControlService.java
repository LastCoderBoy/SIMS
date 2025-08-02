package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.InventoryMetrics;
import com.JK.SIMS.models.IC_models.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.outgoing.OrderResponseDto;
import com.JK.SIMS.models.IC_models.outgoing.OrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.InventoryControl_service.outgoingStockService.OutgoingStockService;
import com.JK.SIMS.service.incomingStock_service.IncomingStockService;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
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

import static com.JK.SIMS.service.GlobalServiceHelper.amongInvalidStatus;
import static com.JK.SIMS.service.InventoryControl_service.InventoryServiceHelper.validateUpdateRequest;

@Service
public class InventoryControlService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    private final ProductManagementService pmService;
    private final OutgoingStockService outgoingStockService;
    private final DamageLossService damageLossService;
    private final IncomingStockService incomingStockService;
    @Autowired
    public InventoryControlService(IC_repository icRepository, @Lazy ProductManagementService pmService, @Lazy OutgoingStockService outgoingStockService,
                                   DamageLossService damageLossService, @Lazy IncomingStockService incomingStockService) {
        this.icRepository = icRepository;
        this.pmService = pmService;
        this.outgoingStockService = outgoingStockService;
        this.damageLossService = damageLossService;
        this.incomingStockService = incomingStockService;
    }

    @Transactional(readOnly = true)
    public InventoryPageResponse getInventoryControlPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            // Used to represent at the end of the page
            PaginatedResponse<OrderResponseDto> allPendingOrderDtos =
                    outgoingStockService.getAllOrdersSorted(page, size, "orderDate", "desc", Optional.of(OrderStatus.PENDING));

            // Create the response object
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse(
                    metrics.getTotalCount(),
                    metrics.getLowStockCount(),
                    incomingStockService.getTotalValidIncomingStockSize(),
                    outgoingStockService.getTotalValidOutgoingStockSize(),
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



    private String generateSKU(String productID, ProductCategories category){
        try {
            if (productID == null || productID.length() < 4) {
                throw new IllegalArgumentException("Product ID must be at least 4 characters (e.g., PRD001)");
            }
            if (category == null) {
                throw new IllegalArgumentException("Category cannot be null");
            }

            String lastDigits = productID.substring(3);
            String categoryStart = category.toString().substring(0, 3);
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


    // Only currentStock and minLevel can be updated in the IC section
    @Transactional
    public ApiResponse updateProduct(String sku, InventoryData newInventoryData) throws BadRequestException {
        try {
            // Validate input parameters
            validateUpdateRequest(newInventoryData);

            // Find and validate the existing product
            InventoryData existingProduct = getInventoryDataBySku(sku);

            // Update stock levels
            updateStockLevels(existingProduct,
                    Optional.of(newInventoryData.getCurrentStock()),
                    Optional.of(newInventoryData.getMinLevel()));

            logger.info("IC (updateProduct): Product with SKU {} updated successfully", sku);
            return new ApiResponse(true, sku + " is updated successfully");

        } catch (DataAccessException da) {
            logger.error("IC (updateProduct): Database error while updating SKU {}: {}",
                    sku, da.getMessage());
            throw new DatabaseException("IC (updateProduct): Database error", da);
        } catch (BadRequestException | ValidationException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("IC (updateProduct): Unexpected error while updating SKU {}: {}",
                    sku, ex.getMessage());
            throw new ServiceException("IC (updateProduct): Internal Service error", ex);
        }
    }

    /**
     * Deletes a product from the inventory control system and archives it in the Product Management system.
     *
     * @param sku The Stock Keeping Unit identifier of the product to delete
     * @return ApiResponse containing success status and confirmation message
     * @throws BadRequestException if product is not found in IC or PM system
     * @throws DatabaseException   if database operation fails
     * @throws ServiceException    if any other error occurs during deletion
     */
    @Transactional
    public ApiResponse deleteProduct(String sku) throws BadRequestException {
        try{
            Optional<InventoryData> product = icRepository.findBySKU(sku);
            if(product.isPresent()){
                InventoryData productToBeDeleted = product.get();
                String id = productToBeDeleted.getPmProduct().getProductID();

                ProductsForPM productInPM = pmService.findProductById(id);

                if(productInPM.getStatus().equals(ProductStatus.ACTIVE) ||
                        productInPM.getStatus().equals(ProductStatus.PLANNING) ||
                        productInPM.getStatus().equals(ProductStatus.ON_ORDER) ) {
                    productInPM.setStatus(ProductStatus.ARCHIVED);
                    pmService.saveProduct(productInPM);
                }

                icRepository.deleteBySKU(sku);

                logger.info("IC (deleteProduct): Product {} is deleted successfully.", sku);
                return new ApiResponse(true, "Product " + sku + " is deleted successfully.");
            }
            throw new BadRequestException("IC (deleteProduct): Product with SKU " + sku + " not found");
        }catch (DataAccessException de){
            throw new DatabaseException("IC (deleteProduct): Database error occurred.", de);
        }catch (BadRequestException be){
            throw be;
        }catch (Exception e){
            throw new ServiceException("IC (deleteProduct): Failed to delete product", e);
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
    public InventoryData getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId)
                .orElseThrow(() -> new ResourceNotFoundException("IC (getInventoryProductByProductId): Inventory Data Not Found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStockLevels(InventoryData existingProduct, Optional<Integer> newStockLevel, Optional<Integer> newMinLevel ) {
        // Update current stock if provided
        newStockLevel.ifPresent(existingProduct::setCurrentStock);

        // Update minimum level if provided
        newMinLevel.ifPresent(existingProduct::setMinLevel);

        //Update the status as well based on the latest update
        InventoryServiceHelper.updateInventoryStatus(existingProduct);

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

            // Update status based on new stock level
            InventoryServiceHelper.updateInventoryStatus(inventory);

            icRepository.save(inventory);
            logger.debug("IC (fulfillReservation): Fulfilled reservation of {} units for product {}", quantity, productId);

        } catch (DataAccessException e) {
            logger.error("IC (fulfillReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to fulfill reservation", e);
        }
    }

    // Release reservation when order is cancelled
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

    // Get available stock (current - reserved)
    public int getAvailableStock(String productId) {
        Optional<InventoryData> inventory = icRepository.findByPmProduct_ProductID(productId);
        if (inventory.isEmpty()) {
            return 0;
        }
        return inventory.get().getCurrentStock() - inventory.get().getReservedStock();
    }
}
