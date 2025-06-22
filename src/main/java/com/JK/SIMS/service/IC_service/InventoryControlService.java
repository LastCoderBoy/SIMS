package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.InventoryException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.*;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.JK.SIMS.service.IC_service.InventoryServiceHelper.validateUpdateRequest;

@Service
public class InventoryControlService {
    private static Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    @Autowired
    public InventoryControlService(IC_repository icRepository) {
        this.icRepository = icRepository;
    }


    /**
     * Loads inventory page data with pagination and aggregated metrics.
     * Retrieves inventory data along with various metrics including total count,
     * low stock items, incoming/outgoing items, and damage/loss items.
     *
     * @param page Zero-based page number
     * @param size Number of items per page
     * @return InventoryPageResponse containing paginated data and metrics
     * @throws DatabaseException if database access fails
     * @throws ServiceException if any other error occurs during data retrieval
     */
    public InventoryPageResponse loadIcPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            PaginatedResponse<InventoryDataDTO> inventoryDtoResponse = getInventoryDto(page, size);

            // Convert to response DTO
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse();

            inventoryPageResponse.setInventoryDataDTOList(inventoryDtoResponse);
            inventoryPageResponse.setTotalInventorySize(metrics.getTotalCount().intValue());
            inventoryPageResponse.setLowStockSize(metrics.getLowStockCount().intValue());
            inventoryPageResponse.setIncomingStockSize(metrics.getIncomingCount().intValue());
            inventoryPageResponse.setOutgoingStockSize(metrics.getOutgoingCount().intValue());
            inventoryPageResponse.setDamageLossSize(metrics.getDamageLossCount().intValue());
            logger.info("IC (loadIcPageData): Sending page {} with {} products.", page, inventoryDtoResponse.getContent().size());
            return inventoryPageResponse;
        } catch (DataAccessException e) {
            logger.error("IC (loadIcPageData): Database access error while retrieving inventory data.", e);
            throw new DatabaseException("IC (loadIcPageData): Failed to retrieve products from database", e);
        } catch (Exception e) {
            logger.error("IC (loadIcPageData): Unexpected error occurred while loading IC page data.", e);
            throw new ServiceException("IC (loadIcPageData): Failed to retrieve products", e);
        }
    }


    /**
     * Performs a comprehensive search across inventory items.
     * Searches through multiple fields including SKU, Location, Status,
     * Category, and Product Name. Returns default paginated data if no search text is provided.
     *
     * @param text Search query text (case-insensitive)
     * @return PaginatedResponse containing the InventoryDataDTO search results
     * @throws DatabaseException if database operation fails
     * @throws ServiceException if any other error occurs during search
     */
    public PaginatedResponse<InventoryDataDTO> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("product.name").ascending());
                Page<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);

                return transformToPaginatedDTOResponse(inventoryData) ;
            }
            logger.info("IC (searchProduct): No search text provided. Retrieving first page with default size.");
            return getInventoryDto(page,size);
        } catch (DataAccessException e) {
            throw new DatabaseException("IC (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("IC (searchProduct): Failed to retrieve products", e);
        }
    }


    /**
     * Filters and sorts inventory products with advanced filtering options.
     * Supports filtering by specific fields using the format "field:value"
     * and general text search if no specific field is specified.
     *
     * Supported filter fields:
     * - status: Matches InventoryDataStatus enum values
     * - stock: Filters by stock level (numeric comparison)
     * - location: Case-insensitive location search
     *
     * @param filter Filter string in "field:value" format or general search term
     * @param sortBy Field name to sort by
     * @param sortDirection Sort direction ("asc" or "desc")
     * @param page Zero-based page number
     * @param size Number of items per page
     * @return PaginatedResponse of InventoryDataDTO containing filtered and sorted results
     * @throws ValidationException if filter value is invalid
     * @throws DatabaseException if database operation fails
     * @throws ServiceException if any other error occurs
     */
    public PaginatedResponse<InventoryDataDTO> filterProducts(String filter, String sortBy, String sortDirection, int page, int size) {
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
                    // Use as a general search term
                    resultPage = icRepository.findByGeneralSearch(filter.trim().toLowerCase(), pageable);
                }
            } else {
                resultPage = icRepository.findAll(pageable);
            }

            logger.info("IC (filterProducts): {} products retrieved.", resultPage.getContent().size());
            return transformToPaginatedDTOResponse(resultPage);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("IC (filterProducts): Invalid filter value: " + iae.getMessage());
        } catch (DataAccessException da) {
            throw new DatabaseException("IC (filterProducts): Database error", da);
        } catch (Exception e) {
            throw new ServiceException("IC (filterProducts): Internal error", e);
        }
    }


    /**
     * Retrieves a paginated list of inventory items sorted by product name.
     * Internal helper method used by other service methods to get paginated data.
     *
     * @param page Zero-based page number
     * @param size Number of items per page
     * @return PaginatedResponse of InventoryDataDTO
     * @throws DatabaseException if database access fails
     * @throws ServiceException if any other error occurs
     */
    private PaginatedResponse<InventoryDataDTO> getInventoryDto(int page, int size) {
        try{
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name").ascending());
            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);
            return transformToPaginatedDTOResponse(inventoryPage);

        } catch (DataAccessException da){
            throw new DatabaseException("IC (getInventoryDataDTOList): Failed to retrieve products due to database error", da);
        } catch (Exception e){
            throw new ServiceException("IC (getInventoryDataDTOList): Failed to retrieve products", e);
        }
    }

    private PaginatedResponse<InventoryDataDTO> transformToPaginatedDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDTO> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream().map(this::convertToDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());

        return dtoResponse;
    }


    /**
     * Converts an InventoryData entity to its DTO representation.
     * Maps all relevant fields including associated product information.
     *
     * @param currentProduct InventoryData entity to convert
     * @return InventoryDataDTO containing the mapped data
     */
    private InventoryDataDTO convertToDTO(InventoryData currentProduct) {
        InventoryDataDTO inventoryDataDTO = new InventoryDataDTO();
        inventoryDataDTO.setInventoryData(currentProduct);
        inventoryDataDTO.setProductName(currentProduct.getProduct().getName());
        inventoryDataDTO.setCategory(currentProduct.getProduct().getCategory());
        return inventoryDataDTO;
    }


    /**
     * Adds a new product to the inventory control system.
     * Initializes a new inventory entry with default values:
     * - CurrentStock: 0
     * - MinLevel: 0
     * - Status: LOW_STOCK
     * - LastUpdate: Current timestamp
     *
     * @param product ProductsForPM object containing product details
     * @throws DatabaseException if database operation fails
     * @throws InventoryException if product data is invalid or operation fails
     * @throws ServiceException if any other error occurs during product addition
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
        } catch (DataAccessException da){
            throw new DatabaseException("IC (addProduct): Failed to save inventory data due to database error", da);
        } catch (InventoryException ex){
            throw ex;
        } catch (Exception e){
            throw new ServiceException("IC: Unexpected error occurred while adding product", e);
        }
    }


    /**
     * Generates a SKU (Stock Keeping Unit) for a new product.
     * Format: [First 3 letters of category]-[Last digits of product ID]
     * Example: "EDU-001" for Education category and product ID "PRD001"
     *
     * @param productID Product ID string (must be at least 4 characters)
     * @param category Product category enum value
     * @return Generated SKU string
     * @throws InventoryException if productID or category is invalid
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


    // Only currentStock and minLevel can be updated in the IC section
    public ApiResponse updateProduct(String sku, InventoryData newInventoryData) throws BadRequestException {
        try {
            // Validate input parameters
            validateUpdateRequest(sku, newInventoryData);

            // Find and validate existing product
            InventoryData existingProduct = icRepository.findBySKU(sku)
                    .orElseThrow(() -> new BadRequestException(
                            "IC (updateProduct): No product with SKU " + sku + " found"));

            // Update stock levels
            updateStockLevels(existingProduct, newInventoryData);

            // Save and return
            icRepository.save(existingProduct);
            logger.info("IC (updateProduct): Product with SKU {} updated successfully", sku);
            return new ApiResponse(true, sku + " is updated successfully");

        } catch (DataAccessException da) {
            logger.error("IC (updateProduct): Database error while updating SKU {}: {}",
                    sku, da.getMessage());
            throw new DatabaseException("IC (updateProduct): Database error", da);
        } catch (BadRequestException | ValidationException e) {
            logger.warn("IC (updateProduct): Validation failed for SKU {}: {}",
                    sku, e.getMessage());
            throw e;
        } catch (Exception ex) {
            logger.error("IC (updateProduct): Unexpected error while updating SKU {}: {}",
                    sku, ex.getMessage());
            throw new ServiceException("IC (updateProduct): Internal Service error", ex);
        }
    }
    private void updateStockLevels(InventoryData existingProduct,
                                   InventoryData newInventoryData) {
        // Update current stock if provided
        if (newInventoryData.getCurrentStock() != null) {
            existingProduct.setCurrentStock(newInventoryData.getCurrentStock());
        }

        // Update minimum level if provided
        if (newInventoryData.getMinLevel() != null) {
            existingProduct.setMinLevel(newInventoryData.getMinLevel());
        }

        //Update the status as well based on the latest update
        updateInventoryStatus(existingProduct);

        // Update last update timestamp
        existingProduct.setLastUpdate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    private void updateInventoryStatus(InventoryData product) {
        if (product.getCurrentStock() <= product.getMinLevel()) {
            product.setStatus(InventoryDataStatus.LOW_STOCK);
        } else {
            product.setStatus(InventoryDataStatus.IN_STOCK);
        }
    }


}
