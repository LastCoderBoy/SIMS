package com.JK.SIMS.service.productManagementService.impl;

import com.JK.SIMS.config.security.JWTService;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.BatchProductResponse;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.orderManagementService.salesOrderService.queryService.SalesOrderQueryService;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import com.JK.SIMS.service.productManagementService.utils.PMServiceHelper;
import com.JK.SIMS.service.utilities.ExcelReporterHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.productManagementService.excelReporter.ExcelReporterForPM.createHeaderRow;
import static com.JK.SIMS.service.productManagementService.excelReporter.ExcelReporterForPM.populateDataRows;
import static com.JK.SIMS.service.utilities.GlobalServiceHelper.amongInvalidStatus;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductManagementServiceImpl implements ProductManagementService {

    // ========== Helpers ==========
    private final PMServiceHelper pmServiceHelper;

    // ========== Components ==========
    private final InventoryQueryService inventoryQueryService;

    // ========== Services ==========
    private final InventoryControlService icService;
    private final JWTService jwtService;
    private final SalesOrderQueryService salesOrderQueryService;

    // ========== Repositories ==========
    private final PM_repository pmRepository;


    private ProductsForPM findProductById(String productId) {
        return pmRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with ID " + productId + " not found"));
    }

    /**
     * Retrieves a paginated list of all products from the database.
     * The products are sorted by productID in ascending order.
     * Each product entity is converted to a DTO before returning.
     *
     * @param page zero-based page number for pagination
     * @param size number of items per page
     * @return PaginatedResponse containing all products in the system
     * @throws DatabaseException if database access fails
     * @throws ServiceException  if any other error occurs during retrieval
     */
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> getAllProducts(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
            Page<ProductsForPM> allProducts = pmRepository.findAll(pageable);
            PaginatedResponse<ProductManagementResponse> dtoPaginatedResponse = pmServiceHelper.transformToDTOPaginatedResponse(allProducts);
            log.info("PM (getAllProducts): Retrieved {} products from database.", allProducts.getTotalElements());
            return dtoPaginatedResponse;
        } catch (DataAccessException e) {
            throw new DatabaseException("PM (getAllProducts): Failed to retrieve products from database", e);
        } catch (Exception e) {
            throw new ServiceException("PM (getAllProducts): Failed to retrieve products", e);
        }
    }


    
    /**
     * Adds a new product to the system after validation and generates a new product ID.
     * If the product status is NOT Equal to PLANNING, it will be added to IC section.
     *
     * @param productRequest The product object containing all required product details
     * @return ApiResponse object containing success status and message
     * @throws ValidationException   if product details are invalid
     * @throws ServiceException      if there's an error during product addition
     */
    @Override
    @Transactional
    public ProductManagementResponse addProduct(ProductManagementRequest productRequest){
        try {
            if (pmServiceHelper.validateProduct(productRequest)) {
                ProductsForPM product = createProductEntity(productRequest);
                ProductsForPM savedProduct = pmRepository.save(product);

                // Add to inventory if status is not PLANNING
                if (!productRequest.getStatus().equals(ProductStatus.PLANNING)) {
                    icService.addProduct(savedProduct, false);
                }
                log.info("PM (addProduct): Product added - ID: {}, Name: {}",
                        savedProduct.getProductID(), savedProduct.getName());

                return pmServiceHelper.convertToDTO(savedProduct);
            }
            throw new ValidationException("PM (addProduct): Invalid product details");
        } catch (ValidationException ve) {
            throw ve;
        } catch (DataIntegrityViolationException e) {
            log.error("PM (addProduct): Duplicate product - {}", e.getMessage());
            throw new ValidationException("Product with this name already exists");
        } catch (Exception e) {
            throw new ServiceException("PM (addProduct): Failed to add product ", e);
        }
    }

    /**
     * Adds multiple products in a single transaction
     * Continues processing even if some products fail (partial success)
     */
    @Override
    @Transactional
    public BatchProductResponse addProductsBatch(List<ProductManagementRequest> products) {
        log.info("PM (addProductsBatch): Processing {} products", products.size());

        List<String> successfulIds = new ArrayList<>();
        List<BatchProductResponse.ProductError> errors = new ArrayList<>();

        for (int i = 0; i < products.size(); i++) {
            ProductManagementRequest productRequest = products.get(i);
            try {
                if (pmServiceHelper.validateProduct(productRequest)) {
                    ProductsForPM product = createProductEntity(productRequest);
                    ProductsForPM savedProduct = pmRepository.save(product);

                    // Add to inventory if needed
                    if (!productRequest.getStatus().equals(ProductStatus.PLANNING)) {
                        icService.addProduct(savedProduct, false);
                    }

                    successfulIds.add(savedProduct.getProductID());
                    log.debug("PM (addProductsBatch): Product {} added successfully", savedProduct.getProductID());
                }
            } catch (Exception e) {
                log.warn("PM (addProductsBatch): Failed to add product at index {} - {}", i, e.getMessage());
                errors.add(new BatchProductResponse.ProductError(
                        i,
                        productRequest,
                        e.getMessage()
                ));
            }
        }

        BatchProductResponse response = BatchProductResponse.builder()
                .totalRequested(products.size())
                .successCount(successfulIds.size())
                .failureCount(errors.size())
                .successfulProductIds(successfulIds)
                .errors(errors)
                .build();

        log.info("PM (addProductsBatch): Completed - {}/{} successful",
                response.getSuccessCount(), response.getTotalRequested());

        return response;
    }

    /**
     * Helper method to create ProductsForPM entity from DTO
     */
    private ProductsForPM createProductEntity(ProductManagementRequest request) {
        ProductsForPM product = new ProductsForPM();
        product.setProductID(generateProductId());
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setLocation(request.getLocation());
        product.setStatus(request.getStatus());
        return product;
    }

    /**
     * Deletes a product from both Product Management and Inventory Control systems.
     * This is a cascading delete operation that removes the product from both PM and IC databases.
     *
     * @param id The unique identifier of the product to be deleted
     * @return ResponseEntity containing success message if deletion is successful
     * @throws ResourceNotFoundException if the product with given ID is not found
     * @throws DatabaseException if there's an error during database operation
     * @throws ServiceException if any other error occurs during deletion
     */
    // Service
    @Override
    @Transactional
    public ApiResponse<Void> deleteProduct(String id, String jwtToken) {
        try {
            String username = jwtService.extractUsername(jwtToken);

            ProductsForPM product = findProductById(id);  // Throws ResourceNotFoundException if not found

            // If the product is used in active orders (prevent accidental deletion)
            long activeOrdersCount = salesOrderQueryService.countActiveOrdersForProduct(id);
            if (activeOrdersCount > 0) {
                throw new ValidationException(
                        "Active orders found: " + activeOrdersCount + ". Cannot delete product with active orders. Please cancel orders first."
                );
            }

            // Delete from IC first (cascade)
            icService.deleteByProductId(id);

            // Delete from PM
            pmRepository.delete(product);

            log.info("PM (deleteProduct): Product {} deleted successfully by {}", id, username);
            return new ApiResponse<>(true, "Product deleted successfully");
        } catch (ResourceNotFoundException | ValidationException e) {
            log.warn("PM (deleteProduct): Cannot delete product {} - {}", id, e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("PM (deleteProduct): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to delete product", e);
        } catch (Exception e) {
            log.error("PM (deleteProduct): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to delete product", e);
        }
    }


    /**
     * Updates an existing product with new information. Only non-null fields from the newProduct
     * will be used to update the existing product.
     *
     * @param productId  The unique identifier of the product to update
     * @param updateProductRequest The product object containing the new values to update
     * @return ApiResponse indicating success or failure of the update operation
     * @throws ResourceNotFoundException if the product with given ID is not found
     * @throws ValidationException if the new location format is invalid
     * @throws ServiceException    if any other error occurs during update
     */
    @Override
    @Transactional
    public ApiResponse<Void> updateProduct(String productId, ProductManagementRequest updateProductRequest) {
        try {
            ProductsForPM currentProduct = findProductById(productId);
            if(pmServiceHelper.isAllFieldsNull(updateProductRequest)){
                log.info("PM (updateProduct): No fields to update. Product with ID {} not updated.", productId);
                return new ApiResponse<>(false, "Missing fields to update.");
            }

            updateProductFields(currentProduct, updateProductRequest);
            updateProductAndInventoryStatus(currentProduct, updateProductRequest, productId);

            pmRepository.save(currentProduct);
            log.info("PM (updateProduct): Product with ID {} updated successfully", productId);

            return new ApiResponse<>(true, "Product with ID " + productId + " updated successfully!");
        } catch (ResourceNotFoundException e) {
            log.error("PM (updateProduct): Product with ID {} not found: {}", productId, e.getMessage());
            throw new ResourceNotFoundException("PM (updateProduct): " + e.getMessage() );
        } catch (ValidationException e) {
            log.error("PM (updateProduct): Invalid location format: {}", e.getMessage());
            throw new ValidationException(e.getMessage());
        } catch (Exception e) {
            log.error("PM (updateProduct): Internal error: {}", e.getMessage());
            throw new ServiceException("PM (updateProduct): Internal error " + productId, e);
        }
    }


    private void updateProductFields(ProductsForPM currentProduct, ProductManagementRequest newProductRequest) {
        if (newProductRequest.getName() != null) {
            currentProduct.setName(newProductRequest.getName());
        }
        if (newProductRequest.getCategory() != null) {
            currentProduct.setCategory(newProductRequest.getCategory());
        }
        if (newProductRequest.getPrice() != null) {
            currentProduct.setPrice(newProductRequest.getPrice());
        }
        if (newProductRequest.getLocation() != null) {
            pmServiceHelper.validateLocationFormat(newProductRequest.getLocation());
            currentProduct.setLocation(newProductRequest.getLocation());
        }
    }

    private void updateProductAndInventoryStatus(ProductsForPM currentProduct, ProductManagementRequest updateProductRequest, String productId) {
        if (updateProductRequest.getStatus() != null && !updateProductRequest.getStatus().equals(currentProduct.getStatus())) {
            Optional<InventoryControlData> productInIcOpt =
                    inventoryQueryService.getInventoryProductByProductId(productId);
            ProductStatus currentStatus = currentProduct.getStatus();
            ProductStatus newStatus = updateProductRequest.getStatus();

            if (pmServiceHelper.validateStatusBeforeAdding(currentStatus, newStatus)) {
                currentProduct.setStatus(newStatus);
                handleStatusChange(currentProduct, newStatus, currentStatus, productInIcOpt);
            } else {
                currentProduct.setStatus(newStatus);
                if (amongInvalidStatus(newStatus)) {
                    icService.updateInventoryStatus(productInIcOpt, InventoryDataStatus.INVALID);
                }
            }
        }
    }

    private void handleStatusChange(ProductsForPM currentProduct, ProductStatus newStatus,
                                    ProductStatus previousStatus, Optional<InventoryControlData> productInIcOpt) {
        if (newStatus.equals(ProductStatus.ACTIVE) || newStatus.equals(ProductStatus.ON_ORDER)) {
            if (previousStatus.equals(ProductStatus.ARCHIVED)) {
                // the status is changing from ARCHIVED -> ACTIVE, ON_ORDER
                if (productInIcOpt.isEmpty()) {
                    icService.addProduct(currentProduct, false);
                }
            } else {
                // Which means the status is changing from PLANNING -> ACTIVE or ON_ORDER
                icService.addProduct(currentProduct, false);
            }
        } else {
            // No need to add to IC, if the product is present in the IC change to INVALID status or else skip it
            icService.updateInventoryStatus(productInIcOpt, InventoryDataStatus.INVALID);
        }
    }


    /**
     * Searches for products based on a text query with pagination support.
     * The search is performed across multiple fields including product ID, name,
     * category, status, and location.
     *
     * @param text The search query text
     * @param page Zero-based page number
     * @param size Number of items per page
     * @return PaginatedResponse after the search operation.
     * @throws DatabaseException if an error occurs accessing the database
     * @throws ServiceException if an error occurs during the search operation
     */
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> searchProduct(String text, int page, int size) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
                Page<ProductsForPM> result = pmRepository.searchProducts(text.trim().toLowerCase(), pageable);
                return pmServiceHelper.transformToDTOPaginatedResponse(result);
            }
            log.info("PM (searchProduct): No search text provided. Retrieving first page with default size.");
            return getAllProducts(0, 10);
        }catch (DataAccessException da) {
            throw new DatabaseException("PM (searchProduct): Database error", da);
        } catch (Exception e) {
            throw new ServiceException("PM (searchProduct): Failed to retrieve products", e);
        }
    }


    /**
     * Filters and sorts products based on specified criteria with pagination support.
     * Filter format: "field:value" where field can be category, location, price, or status.
     * Also supports general text search if filter doesn't match the field:value format.
     *
     * @param filter    Filter string in "field:value" format or general search term
     * @param sortBy    Field to sort by (e.g. "productID", "name", "price")
     * @param direction Sort direction ("asc" or "desc")
     * @param page      Zero-based page number
     * @param size      Number of items per page
     * @return PaginatedResponse containing filtered and sorted ProductManagementDTO objects
     * @throws ValidationException if filter value is invalid
     * @throws DatabaseException   if database operation fails
     * @throws ServiceException    if any other error occurs
     */
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> filterProducts(String filter, String sortBy, String direction, int page, int size) {
        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<ProductsForPM> resultPage;
            if(filter != null && !filter.trim().isEmpty()){
                String[] filterParts = filter.split(":");
                if(filterParts.length == 2){
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1];
                    resultPage = switch (field) {
                        case "category" -> {
                            ProductCategories category = ProductCategories.valueOf(value.toUpperCase());
                            yield pmRepository.findByCategory(category, pageable);
                        }
                        case "location" -> pmRepository.findByLocation(value, pageable);
                        case "price" -> pmRepository.findByPriceLevel(Integer.parseInt(value), pageable);
                        case "status" -> {
                            ProductStatus status = ProductStatus.valueOf(value.toUpperCase());
                            yield pmRepository.findByStatus(status, pageable);
                        }
                        default -> pmRepository.findAll(pageable);
                    };
                }
                else {
                    resultPage = pmRepository.findByGeneralFilter(filter.trim().toLowerCase(), pageable);
                }
            }
            else {
                resultPage = pmRepository.findAll(pageable);
            }

            return pmServiceHelper.transformToDTOPaginatedResponse(resultPage);

        } catch (IllegalArgumentException iae) {
            log.error("PM (filterProducts): Invalid filter value: {}", iae.getMessage());
            throw new ValidationException("PM (filterProducts): Invalid filter value");
        } catch (DataAccessException da) {
            log.error("PM (filterProducts): Database error: {}", da.getMessage());
            throw new DatabaseException("PM (filterProducts): Database error", da);
        } catch (Exception e) {
            log.error("PM (filterProducts): Failed to filter products: {}", e.getMessage());
            throw new ServiceException("PM (filterProducts): Failed to filter products", e);
        }
    }

    /**
     * Retrieves all products from the database sorted by productID in ascending order.
     * This method is primarily used for report generation and returns the full list without pagination.
     * Each product entity is converted to a DTO before being added to the returned list.
     *
     * @return List of ProductManagementDTO objects containing all products in the system
     * @throws DatabaseException if there is an error accessing the database
     * @throws ServiceException  if any other error occurs during the retrieval process
     */
    private List<ProductManagementResponse> getAllProducts() {
        List<ProductsForPM> productList = pmRepository.findAll(Sort.by("productID").ascending());
        return productList.stream().map(pmServiceHelper::convertToDTO).toList();
    }

    /**
     * Generates an Excel report containing product management data.
     * Creates a workbook with product details including ID, category, name,
     * location, price, and status.
     *
     * @param response HttpServletResponse to write the Excel file to
     */
    @Override
    public void generatePMReport(HttpServletResponse response) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Product Management");
        createHeaderRow(sheet);
        List<ProductManagementResponse> allProducts = getAllProducts();
        populateDataRows(sheet, allProducts);
        log.info("PM (GeneratePmReport): Retrieved {} products from database for report generation.)", allProducts.size());
        ExcelReporterHelper.writeWorkbookToResponse(response, workbook);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveProduct(ProductsForPM product) {
        try {
            pmRepository.save(product);
            log.info("PM (saveProduct): Successfully saved/updated product with ID {}",
                    product.getProductID());
        } catch (DataAccessException da) {
            log.error("PM (saveProduct): Database error while saving product: {}",
                    da.getMessage());
            throw new DatabaseException("Failed to save product", da);
        } catch (Exception e) {
            log.error("PM (saveProduct): Unexpected error while saving product: {}",
                    e.getMessage());
            throw new ServiceException("Failed to save product", e);
        }
    }

    private String generateProductId() {
        Optional<String> lastIdOpt = pmRepository.getLastId();
        if (lastIdOpt.isPresent()) {
            String lastId = lastIdOpt.get();
            int lastNumber = Integer.parseInt(lastId.substring(3));
            return String.format("PRD%03d", lastNumber + 1);
        }
        return "PRD001";
    }
}







