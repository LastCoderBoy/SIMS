package com.JK.SIMS.service.InventoryServices.totalItemsService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlRequest;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.totalItemsService.filterLogic.InventorySpecification;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.productManagementService.impl.PMServiceHelper;
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

import static com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper.validateUpdateRequest;
import static com.JK.SIMS.service.utilities.ExcelReporterHelper.*;

@Service
public class TotalItemsService {
    private static final Logger logger = LoggerFactory.getLogger(TotalItemsService.class);
    private static final String DEFAULT_SORT_BY = "pmProduct.name";
    private static final String DEFAULT_SORT_DIRECTION = "asc";

    private final GlobalServiceHelper globalServiceHelper;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final PMServiceHelper pmServiceHelper;
    private final StockManagementLogic stockManagementLogic;

    private final IC_repository icRepository; // We are working with the Inventory Products so that's why we need this repository
    @Autowired
    public TotalItemsService(GlobalServiceHelper globalServiceHelper, InventoryServiceHelper inventoryServiceHelper,
                             PMServiceHelper pmServiceHelper, StockManagementLogic stockManagementLogic, IC_repository icRepository) {
        this.globalServiceHelper = globalServiceHelper;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.pmServiceHelper = pmServiceHelper;
        this.stockManagementLogic = stockManagementLogic;
        this.icRepository = icRepository;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> getPaginatedInventoryDto(String sortBy, String sortDirection, int page, int size) {
        try{
            globalServiceHelper.validatePaginationParameters(page, size);
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get the data
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryControlData> inventoryPage = icRepository.findAll(pageable);
            return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(inventoryPage);
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
    public ApiResponse<Void> updateProduct(String sku, InventoryControlRequest inventoryControlRequest) {
        try {
            // Validate input parameters
            validateUpdateRequest(inventoryControlRequest);

            // Find and validate the existing product
            InventoryControlData existingProduct = inventoryServiceHelper.getInventoryDataBySku(sku); // might throw ResourceNotFoundException

            // Update stock levels
            stockManagementLogic.updateInventoryStockLevels(existingProduct,
                    Optional.ofNullable(inventoryControlRequest.getCurrentStock()),
                    Optional.ofNullable(inventoryControlRequest.getMinLevel()));

            logger.info("IcTotalItems (updateProduct): Product with SKU {} updated successfully", sku);
            return new ApiResponse<>(true, sku + " is updated successfully");

        } catch (DataAccessException da) {
            logger.error("IcTotalItems (updateProduct): Database error while updating SKU {}: {}",
                    sku, da.getMessage());
            throw new DatabaseException("IC (updateProduct): Database error", da);
        } catch (ResourceNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception ex) {
            logger.error("IcTotalItems (updateProduct): Unexpected error while updating SKU {}: {}",
                    sku, ex.getMessage(), ex);
            throw new ServiceException("IcTotalItems (updateProduct): Internal Service error", ex);
        }
    }

    // Search by SKU, Location, ID, Name, Category.
    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_BY).ascending());
                Page<InventoryControlData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                logger.info("TotalItems (searchProduct): {} products retrieved.", inventoryData.getContent().size());
                return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(inventoryData) ;
            }
            logger.info("TotalItems (searchProduct): No search text provided. Retrieving first page with default size.");
            return getPaginatedInventoryDto(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION, page,size);
        } catch (DataAccessException e) {
            throw new DatabaseException("TotalItems (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("TotalItems (searchProduct): Failed to retrieve products", e);
        }
    }



    public PaginatedResponse<InventoryControlResponse> filterProducts(String filterBy, String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);

            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Handle filtering
            Page<InventoryControlData> resultPage;
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
                    Specification<InventoryControlData> specification;
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
            return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(resultPage);
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
    public ApiResponse<Void> deleteProduct(String sku) throws BadRequestException {
        try{
            Optional<InventoryControlData> product = icRepository.findBySKU(sku);
            if(product.isPresent()){
                InventoryControlData productToBeDeleted = product.get();
                ProductsForPM productInPM = productToBeDeleted.getPmProduct();

                if(productInPM.getStatus().equals(ProductStatus.ACTIVE) || productInPM.getStatus().equals(ProductStatus.PLANNING)
                        || productInPM.getStatus().equals(ProductStatus.ON_ORDER) ) {
                    productInPM.setStatus(ProductStatus.ARCHIVED);
                    pmServiceHelper.saveProduct(productInPM);
                }
                icRepository.deleteBySKU(sku);
                logger.info("IC (deleteProduct): Product {} is deleted successfully.", sku);
                return new ApiResponse<>(true, "Product " + sku + " is deleted successfully.");
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

    public void generateTotalItemsReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("All Inventory Products");
        createHeaderRowForInventoryDto(sheet);
        List<InventoryControlResponse> allProducts = getAllInventoryProducts(sortBy, sortDirection);
        populateDataRowsForInventoryDto(sheet, allProducts);
        logger.info("TotalItems (generateTotalItemsReport): {} products retrieved.", allProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

    // Helper methods
    public List<InventoryControlResponse> getAllInventoryProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            List<InventoryControlData> inventoryList = icRepository.findAll(sort);

            // Convert to DTOs
            return inventoryList.stream().map(inventoryServiceHelper::convertToInventoryDTO).toList();
        } catch (DataAccessException da) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("TotalItems (getAllInventoryData): Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("TotalItems (getAllInventoryData): Failed to retrieve products", e);
        }
    }
}
