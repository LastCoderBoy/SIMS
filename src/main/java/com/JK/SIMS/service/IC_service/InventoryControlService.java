package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.InventoryException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.*;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
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
     * Returns InventoryPageResponse with paginated InventoryDataDTO list and aggregated metrics.
     * @param page requested page (zero-based)
     * @param size number of items per page
     * @return InventoryPageResponse
     */
    //Which loads all information of the IC page.
    public InventoryPageResponse loadIcPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            List<InventoryDataDTO> inventoryDataDTOStorage = getInventoryDataLoadList(page, size);

            // Create the response DTO
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse();
            inventoryPageResponse.setInventoryDataDTOList(inventoryDataDTOStorage);
            inventoryPageResponse.setTotalInventorySize(metrics.getTotalCount().intValue());
            inventoryPageResponse.setLowStockSize(metrics.getLowStockCount().intValue());
            inventoryPageResponse.setIncomingStockSize(metrics.getIncomingCount().intValue());
            inventoryPageResponse.setOutgoingStockSize(metrics.getOutgoingCount().intValue());
            inventoryPageResponse.setDamageLossSize(metrics.getDamageLossCount().intValue());
            logger.info("IC (loadIcPageData): Sending page {} with {} products.", page, inventoryDataDTOStorage.size());
            return inventoryPageResponse;
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
     * @return ResponseEntity with List of InventoryDataDTO or full inventory data if a text is empty
     */
    public ResponseEntity<?> searchProduct(String text) {

        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                List<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase());

                List<InventoryDataDTO> searchResult = inventoryData.stream()
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
     * Filter and sort inventory products with pagination.
     * @param filter Optional filter string in format "field:value". Supported fields: status, stock, location
     * @param sortBy Field to sort by
     * @param sortDirection Sort direction (asc/desc)
     * @param page zero-based page number
     * @param size page size
     * @return ResponseEntity with Page of filtered and sorted InventoryDataLoad
     */
    public ResponseEntity<?> filterProducts(String filter, String sortBy, String sortDirection, int page, int size) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Handle filtering
            Page<InventoryData> resultPage;
            if (filter != null && !filter.trim().isEmpty()) {
                String[] filterParts = filter.trim().split(":");
                if (filterParts.length == 2) {
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1];

                    resultPage = switch (field) {
                        case "status" -> {
                            InventoryDataStatus status = InventoryDataStatus.valueOf(value.toUpperCase());
                            yield icRepository.findByStatus(status, pageable);
                        }
                        case "stock" -> icRepository.findByStockLevel(Integer.parseInt(value), pageable);
                        case "location" -> icRepository.findByLocationContainingIgnoreCase(value, pageable);
                        default -> icRepository.findAll(pageable);
                    };
                } else {
                    // Use as general search term
                    resultPage = icRepository.findByGeneralSearch(filter.trim().toLowerCase(), pageable);
                }
            } else {
                resultPage = icRepository.findAll(pageable);
            }

            Page<InventoryDataDTO> responseData = resultPage.map(this::transformToInventoryDataLoadSingle);

            logger.info("IC (filterProducts): {} products retrieved.", responseData.getContent().size());
            return new ResponseEntity<>(responseData, HttpStatus.OK);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("IC (filterProducts): Invalid filter value: " + iae.getMessage());
        } catch (DataAccessException da) {
            throw new DatabaseException("IC (filterProducts): Database error", da);
        } catch (Exception e) {
            throw new ServiceException("IC (filterProducts): Internal error", e);
        }
    }


    private List<InventoryDataDTO> getInventoryDataLoadList(int page, int size) {
        try{
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name").ascending());

            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);

            List<InventoryDataDTO> inventoryDataDTOStorage = inventoryPage.stream()
                    .map(this::transformToInventoryDataLoadSingle)
                    .collect(Collectors.toList());
            return inventoryDataDTOStorage;
        }
        catch (DataAccessException da){
            throw new DatabaseException("IC (getInventoryDataDTOList): Failed to retrieve products due to database error", da);
        }
        catch (Exception e){
            throw new ServiceException("IC (getInventoryDataDTOList): Failed to retrieve products", e);
        }
    }

    /**
     * Helper method to transform a single InventoryData entity to InventoryDataDTO DTO.
     * @param currentProduct InventoryData entity
     * @return InventoryDataDTO DTO
     */
    private InventoryDataDTO transformToInventoryDataLoadSingle(InventoryData currentProduct) {
        InventoryDataDTO inventoryDataDTO = new InventoryDataDTO();
        inventoryDataDTO.setInventoryData(currentProduct);
        inventoryDataDTO.setProductName(currentProduct.getProduct().getName());
        inventoryDataDTO.setCategory(currentProduct.getProduct().getCategory());
        return inventoryDataDTO;
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
     * @return String representation of EDU-001 as an example.
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
