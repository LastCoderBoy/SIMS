package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.*;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossMetrics;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.repository.PM_repo.PM_repository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.JK.SIMS.service.GlobalServiceHelper.amongInvalidStatus;
import static com.JK.SIMS.service.InventoryControl_service.InventoryServiceHelper.validateUpdateRequest;

@Service
public class InventoryControlService {
    private static Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final PM_repository pmRepository;
    private final IC_repository icRepository;
    private final DamageLoss_repository damageLoss_repository;
    @Autowired
    public InventoryControlService(PM_repository pmRepository, IC_repository icRepository, DamageLoss_repository damageLoss_repository) {
        this.pmRepository = pmRepository;
        this.icRepository = icRepository;
        this.damageLoss_repository = damageLoss_repository;
    }


    public InventoryPageResponse getInventoryControlPageData(int page, int size) {
        try {
            InventoryMetrics metrics = icRepository.getInventoryMetrics();

            PaginatedResponse<InventoryDataDto> inventoryDtoResponse = getInventoryDto(page, size);

            // Convert to Page Response DTO
            InventoryPageResponse inventoryPageResponse = new InventoryPageResponse();

            inventoryPageResponse.setInventoryDataDTOList(inventoryDtoResponse);
            inventoryPageResponse.setTotalInventorySize(metrics.getTotalCount().intValue());

            // Product is not part of the low stock when the status is on INVALID.
            inventoryPageResponse.setLowStockSize(metrics.getLowStockCount().intValue());

            inventoryPageResponse.setIncomingStockSize(metrics.getIncomingCount().intValue());
            inventoryPageResponse.setOutgoingStockSize(metrics.getOutgoingCount().intValue());

            DamageLossMetrics damageLossMetrics = getDamageLossMetrics();
            inventoryPageResponse.setDamageLossSize(damageLossMetrics.getTotalReport());
            logger.info("IC (getInventoryControlPageData): Sending page {} with {} products.", page, inventoryDtoResponse.getContent().size());
            return inventoryPageResponse;
        } catch (DataAccessException e) {
            logger.error("IC (getInventoryControlPageData): Database access error while retrieving inventory data.", e);
            throw new DatabaseException("IC (getInventoryControlPageData): Failed to retrieve products from database", e);
        } catch (Exception e) {
            logger.error("IC (getInventoryControlPageData): Unexpected error occurred while loading IC page data.", e);
            throw new ServiceException("IC (getInventoryControlPageData): Failed to retrieve products", e);
        }
    }

    private DamageLossMetrics getDamageLossMetrics() {
        try {
            return damageLoss_repository.getDamageLossMetrics();
        } catch (DataAccessException de) {
            throw new DatabaseException("IC (getDamageLossMetrics): Failed to retrieve damage/loss metrics", de);
        } catch (Exception e) {
            throw new ServiceException("IC (getDamageLossMetrics): Unexpected error retrieving damage/loss metrics", e);
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
    private PaginatedResponse<InventoryDataDto> getInventoryDto(int page, int size) {
        try{
            Pageable pageable = PageRequest.of(page, size, Sort.by("pmProduct.name").ascending());
            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);
            return transformToPaginatedDTOResponse(inventoryPage);

        } catch (DataAccessException da){
            throw new DatabaseException("IC (getInventoryDataDTOList): Failed to retrieve products due to database error", da);
        } catch (Exception e){
            throw new ServiceException("IC (getInventoryDataDTOList): Failed to retrieve products", e);
        }
    }


    private PaginatedResponse<InventoryDataDto> transformToPaginatedDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDto> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream().map(this::convertToDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());

        return dtoResponse;
    }


    private InventoryDataDto convertToDTO(InventoryData currentProduct) {
        InventoryDataDto inventoryDataDTO = new InventoryDataDto();
        inventoryDataDTO.setInventoryData(currentProduct);
        inventoryDataDTO.setProductName(currentProduct.getPmProduct().getName());
        inventoryDataDTO.setCategory(currentProduct.getPmProduct().getCategory());
        return inventoryDataDTO;
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
    public PaginatedResponse<InventoryDataDto> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("pmProduct.name").ascending());
                Page<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                logger.info("IC (searchProduct): {} products retrieved.", inventoryData.getContent().size());
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
    public PaginatedResponse<InventoryDataDto> filterProducts(String filter, String sortBy, String sortDirection, int page, int size) {
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
            inventoryData.setLastUpdate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
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

    private InventoryData getInventoryDataBySku(String sku) throws BadRequestException {
        return icRepository.findBySKU(sku)
                .orElseThrow(() -> new BadRequestException(
                        "IC (updateProduct): No product with SKU " + sku + " found"));
    }

    // Helper method.
    @Transactional(readOnly = true)
    public InventoryData getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId)
                .orElseThrow(() -> new ResourceNotFoundException("IC (getInventoryProductByProductId): Inventory Data Not Found"));
    }

    @Transactional
    public InventoryData getInventoryProductByProductIdWithLock(String productId) {
        return icRepository.findByPmProduct_ProductIDWithPessimisticLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("IC (getInventoryProductByProductId): Inventory Data Not Found for product " + productId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStockLevels(InventoryData existingProduct, Optional<Integer> newStockLevel, Optional<Integer> newMinLevel ) {
        // Update current stock if provided
        newStockLevel.ifPresent(existingProduct::setCurrentStock);

        // Update minimum level if provided
        newMinLevel.ifPresent(existingProduct::setMinLevel);

        //Update the status as well based on the latest update
        InventoryServiceHelper.updateInventoryStatus(existingProduct);

        // Update last update timestamp
        existingProduct.setLastUpdate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

        icRepository.save(existingProduct);
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

                Optional<ProductsForPM> productInPM = pmRepository.findById(id);

                if(productInPM.isEmpty()){
                    logger.warn("IC (deleteProduct): Product with SKU {} not found in PM, searched with {} ID", sku, id);
                    throw new BadRequestException("IC (deleteProduct): Product with SKU " + sku + " not found in PM");
                }
                if(productInPM.get().getStatus().equals(ProductStatus.ACTIVE) ||
                        productInPM.get().getStatus().equals(ProductStatus.PLANNING) ||
                        productInPM.get().getStatus().equals(ProductStatus.ON_ORDER) ) {
                    productInPM.get().setStatus(ProductStatus.ARCHIVED);
                    pmRepository.save(productInPM.get());
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
}
