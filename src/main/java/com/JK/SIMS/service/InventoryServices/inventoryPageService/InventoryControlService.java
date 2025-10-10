package com.JK.SIMS.service.InventoryServices.inventoryPageService;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.IC_models.inventoryData.*;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.damageLossService.DamageLossService;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic.PendingOrdersSearchStrategy;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.utilities.GlobalServiceHelper.amongInvalidStatus;

@Service
public class InventoryControlService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    private final SoServiceInIc soServiceInIc;
    private final DamageLossService damageLossService;
    private final PoServiceInIc poServiceInIc;
    private final GlobalServiceHelper globalServiceHelper;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final PendingOrdersSearchStrategy searchPendingStrategy;

    @Autowired
    public InventoryControlService(IC_repository icRepository, SoServiceInIc soServiceInIc,
                                   DamageLossService damageLossService, PoServiceInIc poServiceInIc, GlobalServiceHelper globalServiceHelper, InventoryServiceHelper inventoryServiceHelper, PendingOrdersSearchStrategy searchPendingStrategy) {
        this.icRepository = icRepository;
        this.soServiceInIc = soServiceInIc;
        this.damageLossService = damageLossService;
        this.poServiceInIc = poServiceInIc;
        this.globalServiceHelper = globalServiceHelper;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.searchPendingStrategy = searchPendingStrategy;
    }

    @Transactional(readOnly = true)
    public InventoryPageResponse getInventoryControlPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            PaginatedResponse<PendingOrdersResponseDto> allPendingOrders = getAllPendingOrders(page, size);

            // Create the response object
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse(
                    metrics.getTotalCount(),
                    metrics.getLowStockCount(), // Checks against the VALID products only
                    poServiceInIc.getTotalValidPoSize(),
                    soServiceInIc.getTotalValidOutgoingStockSize(),
                    damageLossService.getDamageLossMetrics().getTotalReport(),
                    allPendingOrders
            );
            logger.info("IC (getInventoryControlPageData): Sending page {} with {} products.", page, allPendingOrders.getContent().size());
            return inventoryPageResponse;
        } catch (DataAccessException e) {
            logger.error("IC (getInventoryControlPageData): Database access error while retrieving inventory data.", e);
            throw new DatabaseException("IC (getInventoryControlPageData): Failed to retrieve products from database", e);
        } catch (Exception e) {
            logger.error("IC (getInventoryControlPageData): Unexpected error occurred while loading IC page data.", e);
            throw new ServiceException("IC (getInventoryControlPageData): Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseDto> getAllPendingOrders(int page, int size) {
        // Fetch all pending orders (SO and PO)
        PaginatedResponse<SalesOrderResponseDto> allPendingSalesOrders =
                soServiceInIc.getAllWaitingSalesOrders(page, size, "orderDate", "desc");

        PaginatedResponse<SummaryPurchaseOrderView> allPendingPurchaseOrders =
                poServiceInIc.getAllPendingPurchaseOrders(page, size, "product.name", "asc");

        // Combine and sort
        List<PendingOrdersResponseDto> combinedResults = new ArrayList<>();
        inventoryServiceHelper.fillWithSalesOrders(combinedResults, allPendingSalesOrders.getContent());
        inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, allPendingPurchaseOrders.getContent());

        return new PaginatedResponse<>(
                new PageImpl<>(combinedResults, PageRequest.of(page, size), combinedResults.size())
        );
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

    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseDto> searchByTextPendingOrders(String text, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                logger.info("IC (searchByTextPendingOrders): Search text provided. Searching for orders with text '{}'", text);
                return searchPendingStrategy.searchInPendingOrders(text, page, size);
            }
            logger.info("IC (searchByTextPendingOrders): No search text provided. Retrieving first page with default size.");
            return getAllPendingOrders(page, size);
        } catch (IllegalArgumentException e) {
            logger.error("IC (searchByTextPendingOrders): Invalid pagination parameters: {}", e.getMessage());
            throw new ValidationException("Invalid pagination parameters");
        } catch (DataAccessException e) {
            logger.error("IC (searchByTextPendingOrders): Database error while searching orders: {}", e.getMessage());
            throw new DatabaseException("Failed to search orders", e);
        } catch (Exception e) {
            logger.error("IC (searchByTextPendingOrders): Unexpected error while searching orders: {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseDto> filterPendingOrders( String type, SalesOrderStatus soStatus, PurchaseOrderStatus poStatus,
                                                                            String dateOption, LocalDate startDate, LocalDate endDate, ProductCategories category,
                                                                            String sortBy, String sortDirection, int page, int size) {

        List<PendingOrdersResponseDto> combinedResults = new ArrayList<>();

        if (type == null && soStatus == null && poStatus == null && category == null && dateOption == null) {
            // Fetch all pending Sales Orders
            PaginatedResponse<SalesOrderResponseDto> salesOrders =
                    soServiceInIc.getAllWaitingSalesOrders(page, size, sortBy, sortDirection);
            inventoryServiceHelper.fillWithSalesOrders(combinedResults, salesOrders.getContent());

            // Fetch all pending Purchase Orders
            PaginatedResponse<SummaryPurchaseOrderView> purchaseOrders =
                    poServiceInIc.getAllPendingPurchaseOrders(page, size, sortBy, sortDirection);
            inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, purchaseOrders.getContent());
        }


        // Handle Sales Orders
        boolean isSalesOrderType = "SALES_ORDER".equalsIgnoreCase(type);
        boolean hasSalesOrderFilters = soStatus != null || category != null || dateOption != null;

        if (isSalesOrderType || hasSalesOrderFilters) {
            if (isSalesOrderType && !hasSalesOrderFilters) {
                // Fetch all Sales Orders (no filters)
                PaginatedResponse<SalesOrderResponseDto> allSalesOrders =
                        soServiceInIc.getAllWaitingSalesOrders(page, size, sortBy, sortDirection);
                inventoryServiceHelper.fillWithSalesOrders(combinedResults, allSalesOrders.getContent());
            } else {
                // Fetch filtered Sales Orders
                PaginatedResponse<SalesOrderResponseDto> salesOrders =
                        soServiceInIc.filterSoProducts(soStatus, category, dateOption, startDate, endDate, page, size);
                inventoryServiceHelper.fillWithSalesOrders(combinedResults, salesOrders.getContent());
            }
        }

        // Handle Purchase Orders
        boolean isPurchaseOrderType = "PURCHASE_ORDER".equalsIgnoreCase(type);
        boolean hasPurchaseOrderFilters = poStatus != null || category != null || dateOption != null;

        if (isPurchaseOrderType || hasPurchaseOrderFilters) {
            if (isPurchaseOrderType && !hasPurchaseOrderFilters) {
                // Fetch all Purchase Orders (no filters)
                PaginatedResponse<SummaryPurchaseOrderView> allPurchaseOrders =
                        poServiceInIc.getAllPendingPurchaseOrders(page, size, sortBy, sortDirection);
                inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, allPurchaseOrders.getContent());
            } else {
                // Fetch filtered Purchase Orders
                PaginatedResponse<SummaryPurchaseOrderView> purchaseOrders =
                        poServiceInIc.filterIncomingPurchaseOrders(poStatus, category, sortBy, sortDirection, page, size);
                inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, purchaseOrders.getContent());
            }
        }

        // Sort combined results
        combinedResults.sort(Comparator.comparing(PendingOrdersResponseDto::getOrderDate).reversed());

        return new PaginatedResponse<>(
                new PageImpl<>(combinedResults, PageRequest.of(page, size), combinedResults.size())
        );
    }


}
