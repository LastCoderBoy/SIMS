package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.service.InventoryControl_service.filterLogic.InventorySpecification;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.InventoryControl_service.InventoryServiceHelper.validateUpdateRequest;
import static com.JK.SIMS.service.utilities.ExcelReporterHelper.*;

@Service
public class TotalItemsService {
    private static final Logger logger = LoggerFactory.getLogger(TotalItemsService.class);
    private static final String DEFAULT_SORT_BY = "pmProduct.name";
    private static final String DEFAULT_SORT_DIRECTION = "asc";

    private final GlobalServiceHelper globalServiceHelper;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final InventoryControlService icService;
    private final ProductManagementService pmService;

    private final IC_repository icRepository; // We are working with the Inventory Products so that's why we need this repository
    @Autowired
    public TotalItemsService(GlobalServiceHelper globalServiceHelper, InventoryServiceHelper inventoryServiceHelper, InventoryControlService icService, ProductManagementService pmService, IC_repository icRepository) {
        this.globalServiceHelper = globalServiceHelper;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.icService = icService;
        this.pmService = pmService;
        this.icRepository = icRepository;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryDataDto> getPaginatedInventoryDto(String sortBy, String sortDirection, int page, int size) {
        try{
            globalServiceHelper.validatePaginationParameters(page, size);
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get the data
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);
            return inventoryServiceHelper.transformToPaginatedDTOResponse(inventoryPage);
        } catch (DataAccessException da){
            logger.error("TotalItems (getInventoryDataDTOList): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("TotalItems (getInventoryDataDTOList): Failed to retrieve products due to database error", da);
        } catch (Exception e){
            logger.error("TotalItems (getInventoryDataDTOList): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("TotalItems (getInventoryDataDTOList): Failed to retrieve products", e);
        }
    }

    // Only currentStock and minLevel can be updated in the IC section
    @Transactional
    public ApiResponse updateProduct(String sku, InventoryData newInventoryData) throws BadRequestException {
        try {
            // Validate input parameters
            validateUpdateRequest(newInventoryData);

            // Find and validate the existing product
            InventoryData existingProduct = icService.getInventoryDataBySku(sku);

            // Update stock levels
            icService.updateStockLevels(existingProduct,
                    Optional.ofNullable(newInventoryData.getCurrentStock()),
                    Optional.ofNullable(newInventoryData.getMinLevel()));

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
                    sku, ex.getMessage(), ex);
            throw new ServiceException("IC (updateProduct): Internal Service error", ex);
        }
    }

    // Search by SKU, Location, ID, Name, Category.
    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryDataDto> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_BY).ascending());
                Page<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                logger.info("TotalItems (searchProduct): {} products retrieved.", inventoryData.getContent().size());
                return inventoryServiceHelper.transformToPaginatedDTOResponse(inventoryData) ;
            }
            logger.info("TotalItems (searchProduct): No search text provided. Retrieving first page with default size.");
            return getPaginatedInventoryDto(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION, page,size);
        } catch (DataAccessException e) {
            throw new DatabaseException("TotalItems (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("TotalItems (searchProduct): Failed to retrieve products", e);
        }
    }



    public PaginatedResponse<InventoryDataDto> filterProducts(String filterBy, String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);

            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Handle filtering
            Page<InventoryData> resultPage;
            if (filterBy != null && !filterBy.trim().isEmpty()) {
                String[] filterParts = filterBy.trim().split(":");
                if (filterParts.length == 2) {
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1];

                    resultPage = switch (field) {
                        case "status" -> {
                            InventoryDataStatus status = InventoryDataStatus.valueOf(value.toUpperCase());
                            yield icRepository.findByStatus(status, pageable);
                        }
                        case "stock" -> icRepository.findByStockLevel(Integer.parseInt(value), pageable);
                        default -> icRepository.findAll(pageable);
                    };
                } else {
                    boolean isStatusType = GlobalServiceHelper.isInEnum(filterBy.trim().toUpperCase(), InventoryDataStatus.class);
                    Specification<InventoryData> specification;
                    if(isStatusType){
                        specification = Specification.where(InventorySpecification.hasStatus(
                                        InventoryDataStatus.valueOf(filterBy.trim().toUpperCase())));
                    }else {
                        specification =
                                Specification.where(InventorySpecification.hasProductCategory(
                                        ProductCategories.valueOf(filterBy.trim().toUpperCase())));
                    }
                    resultPage = icRepository.findAll(specification, pageable);
                }
            } else {
                resultPage = icRepository.findAll(pageable);
            }

            logger.info("TotalItems (filterProducts): {} products retrieved.", resultPage.getContent().size());
            return inventoryServiceHelper.transformToPaginatedDTOResponse(resultPage);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("TotalItems (filterProducts): Invalid filterBy value: " + iae.getMessage());
        } catch (DataAccessException da) {
            logger.error("TotalItems (filterProducts): Database error while filtering by '{}': {}",
                    filterBy, da.getMessage(), da);
            throw new DatabaseException("TotalItems (filterProducts): Database error", da.getCause());
        } catch (Exception e) {
            logger.error("TotalItems (filterProducts): Internal error while filtering by '{}': {})", filterBy, e.getMessage(), e);
            throw new ServiceException("TotalItems (filterProducts): Internal error", e);
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
                ProductsForPM productInPM = productToBeDeleted.getPmProduct();

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

    public void generateReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("All Inventory Products");
        createHeaderRowForInventoryDto(sheet);

        List<InventoryDataDto> allProducts = getAllInventoryProducts(sortBy, sortDirection);
        populateDataRowsForInventoryDto(sheet, allProducts);
        logger.info("TotalItems (generateReport): {} products retrieved.", allProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

    // Helper methods
    public List<InventoryDataDto> getAllInventoryProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            List<InventoryData> inventoryList = icRepository.findAll(sort);

            // Convert to DTOs
            return inventoryList.stream().map(inventoryServiceHelper::convertToDTO).toList();
        } catch (DataAccessException da) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("TotalItems (getAllInventoryData): Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("TotalItems (getAllInventoryData): Failed to retrieve products", e);
        }
    }
}
