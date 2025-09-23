package com.JK.SIMS.service.InventoryServices.inventoryPageService;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryMetrics;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.damageLossService.DamageLossService;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
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
    private final SoServiceInIc soServiceInIc;
    private final DamageLossService damageLossService;
    private final PoServiceInIc poServiceInIc;
    private final InventoryServiceHelper inventoryServiceHelper;
    @Autowired
    public InventoryControlService(IC_repository icRepository,
                                   @Lazy SoServiceInIc soServiceInIc, DamageLossService damageLossService,
                                   @Lazy PoServiceInIc poServiceInIc, InventoryServiceHelper inventoryServiceHelper) {
        this.icRepository = icRepository;
        this.soServiceInIc = soServiceInIc;
        this.damageLossService = damageLossService;
        this.poServiceInIc = poServiceInIc;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Transactional(readOnly = true)
    public InventoryPageResponse getInventoryControlPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            //TODO: Display Incoming(PO) order as well at the end of the page.

            // OUTGOING(SO) PENDING orders will be displayed at the end of the page
            PaginatedResponse<SalesOrderResponseDto> allPendingOrderDtos =
                    soServiceInIc.getAllWaitingSalesOrders(page, size, "orderDate", "desc");

            // Create the response object
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse(
                    metrics.getTotalCount(),
                    metrics.getLowStockCount(), // Checks against the VALID products only
                    poServiceInIc.getTotalValidPoSize(),
                    soServiceInIc.getTotalValidOutgoingStockSize(),
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
}
