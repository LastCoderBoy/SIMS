package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.InventoryException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.*;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.IC_service.IC_serviceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InventoryControlService {
    private static Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    @Autowired
    public InventoryControlService(IC_repository icRepository) {
        this.icRepository = icRepository;
    }


    /**
     * Loads inventory page data with pagination.
     * Returns InventoryDataResponse with paginated InventoryDataLoad list and aggregated metrics.
     * @param page requested page (zero-based)
     * @param size number of items per page
     * @return InventoryDataResponse
     */
    //Which loads all information of the IC page.
    public InventoryDataResponse loadIcPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            List<InventoryDataLoad> inventoryDataLoadStorage = getInventoryDataLoadList(page, size);

            // Create the response DTO
            InventoryDataResponse inventoryDataResponse = new InventoryDataResponse();
            inventoryDataResponse.setInventoryDataLoadList(inventoryDataLoadStorage);
            inventoryDataResponse.setTotalInventorySize(metrics.getTotalCount().intValue());
            inventoryDataResponse.setLowStockSize(metrics.getLowStockCount().intValue());
            inventoryDataResponse.setIncomingStockSize(metrics.getIncomingCount().intValue());
            inventoryDataResponse.setOutgoingStockSize(metrics.getOutgoingCount().intValue());
            inventoryDataResponse.setDamageLossSize(metrics.getDamageLossCount().intValue());
            logger.info("IC (loadIcPageData): Sending page {} with {} products.", page, inventoryDataLoadStorage.size());
            return inventoryDataResponse;
        }
        catch (DataAccessException e) {
            logger.error("IC (loadIcPageData): Database access error while retrieving inventory data.", e);
            throw new DatabaseException("IC (loadIcPageData): Failed to retrieve products from database", e);
        }
        catch (Exception e) {
            logger.error("IC (loadIcPageData): Unexpected error occurred while loading IC page data.", e);
            throw new ServiceException("IC (loadIcPageData): Failed to retrieve products", e);
        }
    }

    /**
     * Search products by SKU, Location, Status, Category and Product Name.
     * @param text search text
     * @return ResponseEntity with List of InventoryDataLoad or full inventory data if a text is empty
     */
    public ResponseEntity<?> searchProduct(String text) {

        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                List<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase());

                List<InventoryDataLoad> searchResult = inventoryData.stream()
                        .map(this::transformToInventoryDataLoadSingle)
                        .collect(Collectors.toList());

                return new ResponseEntity<>(searchResult, HttpStatus.OK);
            }
            logger.info("IC (searchProduct): No search text provided. Retrieving first page with default size.");
            return new ResponseEntity<>(getInventoryDataLoadList(0, 10), HttpStatus.OK);
        }
        catch (Exception e) {
            throw new ServiceException("IC (searchProduct): Failed to retrieve products", e);
        }
    }


    /**
     * Filter products by currentStock and location sorting, and optional status.
     * Sorting is applied in-memory on the paged results.
     * @param sortByCurrentStock boolean flag to sort by current stock ascending
     * @param sortByLocation boolean flag to sort by location ascending
     * @param status optional status filter
     * @param page zero-based page number
     * @param size page size
     * @return ResponseEntity with List of filtered and sorted InventoryDataLoad
     */
    public ResponseEntity<?> filterProducts(boolean sortByCurrentStock, boolean sortByLocation, String status, int page, int size) {
        try {
            Sort sort = Sort.unsorted();

            if (sortByLocation) {
                sort = sort.and(Sort.by("location"));
            }
            if (sortByCurrentStock) {
                sort = sort.and(Sort.by("currentStock"));
            }

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryData> resultPage;

            if (status != null && !status.trim().isEmpty()) {
                InventoryDataStatus inventoryDataStatus = InventoryDataStatus.valueOf(status.toUpperCase().trim());
                resultPage = icRepository.findByStatus(inventoryDataStatus, pageable);
            } else {
                resultPage = icRepository.findAll(pageable);
            }

            List<InventoryDataLoad> inventoryDataLoadList = resultPage.getContent().stream()
                    .map(this::transformToInventoryDataLoadSingle)
                    .collect(Collectors.toList());

            logger.info("IC (filterProducts): {} products retrieved.", inventoryDataLoadList.size());
            return new ResponseEntity<>(inventoryDataLoadList, HttpStatus.OK);
        }
        catch (IllegalArgumentException iae) {
            throw new ValidationException("IC (filterProducts): Invalid status value.");
        }
        catch (DataAccessException da) {
            throw new DatabaseException("IC (filterProducts): Failed to retrieve products due to database error", da);
        }
        catch (Exception e) {
            throw new ServiceException("IC (filterProducts): Internal Service Error!", e);
        }
    }


    private List<InventoryDataLoad> getInventoryDataLoadList(int page, int size) {
        try{
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name").ascending());

            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);

            List<InventoryDataLoad> inventoryDataLoadStorage = inventoryPage.stream()
                    .map(this::transformToInventoryDataLoadSingle)
                    .collect(Collectors.toList());
            return inventoryDataLoadStorage;
        }
        catch (DataAccessException da){
            throw new DatabaseException("IC (getInventoryDataLoadList): Failed to retrieve products due to database error", da);
        }
        catch (Exception e){
            throw new ServiceException("IC (getInventoryDataLoadList): Failed to retrieve products", e);
        }
    }

    /**
     * Helper method to transform a single InventoryData entity to InventoryDataLoad DTO.
     * @param currentProduct InventoryData entity
     * @return InventoryDataLoad DTO
     */
    private InventoryDataLoad transformToInventoryDataLoadSingle(InventoryData currentProduct) {
        InventoryDataLoad inventoryDataLoad = new InventoryDataLoad();
        inventoryDataLoad.setInventoryData(currentProduct);
        inventoryDataLoad.setProductName(currentProduct.getProduct().getName());
        inventoryDataLoad.setCategory(currentProduct.getProduct().getCategory());
        return inventoryDataLoad;
    }


    /** {@code CurrentStock} & {@code MinLevel} set to 0 and {@code Status} to LOW_STOCK intentionally when the PM.addProduct() method is called.
     * because the IC section will handle updates to these values later (when stock is received or ordered)
     */
    public void addProduct(ProductsForPM product){
        try {
            InventoryData inventoryData = new InventoryData();
            String sku = generateSKU(product.getProductID(), product.getCategory());
            inventoryData.setSKU(sku);
            inventoryData.setProduct(product);
            inventoryData.setLocation(product.getLocation());
            inventoryData.setCurrentStock(0);

            inventoryData.setLastUpdate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            inventoryData.setMinLevel(0);
            inventoryData.setStatus(InventoryDataStatus.LOW_STOCK);
            icRepository.save(inventoryData);
            logger.info("IC: New product is added with the {} SKU", sku);
        }
        catch (DataAccessException da){
            logger.error("IC: Database error while adding product with productID {}: {}",
                    product.getProductID(), da.getMessage());
            throw new DatabaseException("IC (addProduct): Failed to save inventory data due to database error", da);
        }
        catch (InventoryException ex){
            throw ex; // Redirects to the Global Exception Handler
        }
        catch (Exception e){
            logger.error("IC: Unexpected error while adding product with productID {}: {}",
                    product.getProductID(), e.getMessage());
            throw new InventoryException("IC: Unexpected error occurred while adding product", e);
        }
    }




    /**
     * Our SKU will be: 3 letters from {@code Category} slash and last digits from the {@code productID}
     * @return EDU-001 as an example.
     */
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


}
