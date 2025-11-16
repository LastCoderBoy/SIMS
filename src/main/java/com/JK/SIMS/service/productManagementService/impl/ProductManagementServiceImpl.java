package com.JK.SIMS.service.productManagementService.impl;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.BatchProductResponse;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryDashboardService.InventoryControlService;
import com.JK.SIMS.service.generalUtils.ExcelReporterHelper;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import com.JK.SIMS.service.productManagementService.utils.PMServiceHelper;
import com.JK.SIMS.service.productManagementService.utils.queryService.ProductQueryService;
import com.JK.SIMS.service.productManagementService.utils.searchService.ProductSearchService;
import com.JK.SIMS.service.salesOrder.salesOrderQueryService.SalesOrderQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.generalUtils.GlobalServiceHelper.amongInvalidStatus;
import static com.JK.SIMS.service.productManagementService.excelReporter.ExcelReporterForPM.createHeaderRow;
import static com.JK.SIMS.service.productManagementService.excelReporter.ExcelReporterForPM.populateDataRows;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductManagementServiceImpl implements ProductManagementService {

    // ========== Helpers ==========
    private final PMServiceHelper pmServiceHelper;

    // ========== Components ==========
    private final SecurityUtils securityUtils;

    // ========== Services ==========
    private final InventoryQueryService inventoryQueryService;
    private final InventoryControlService icService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final ProductQueryService productQueryService;
    private final ProductSearchService productSearchService;

    // ========== Repositories ==========
    private final PM_repository pmRepository;


    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> getAllProducts(String sortBy, String sortDirection, int page, int size) {
        // Delegate to the QueryService
        Page<ProductsForPM> pagedProductsEntity =
                productQueryService.getAllProducts(sortBy, sortDirection, page, size);
        log.info("PM (getAllProducts): Retrieved {} products from database.", pagedProductsEntity.getTotalElements());
        return pmServiceHelper.transformToPaginatedResponse(pagedProductsEntity);
    }

    @Override
    @Transactional
    public ProductManagementResponse addProduct(ProductManagementRequest productRequest){
        try {
            if (pmServiceHelper.validateProduct(productRequest)) {
                ProductsForPM product = pmServiceHelper.createProductEntity(productRequest);
                product.setProductID(generateProductId());
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
                    ProductsForPM product = pmServiceHelper.createProductEntity(productRequest);
                    product.setProductID(generateProductId());
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
            String username = securityUtils.validateAndExtractUsername(jwtToken);

            ProductsForPM product = productQueryService.findById(id);  // Throws ResourceNotFoundException if not found

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
            ProductsForPM currentProduct = productQueryService.findById(productId);
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


    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> searchProduct(String text, String sortBy, String sortDirection, int page, int size) {
        Page<ProductsForPM> searchResult =
                productSearchService.searchProduct(text, sortBy, sortDirection, page, size);
        return pmServiceHelper.transformToPaginatedResponse(searchResult);
    }


    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementResponse> filterProducts(String filter, String sortBy, String direction, int page, int size) {
        Page<ProductsForPM> pagedFilterResponse =
                productSearchService.filterProducts(filter, sortBy, direction, page, size);
        return pmServiceHelper.transformToPaginatedResponse(pagedFilterResponse);
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

        // Get All Products and populate data rows
        List<ProductsForPM> productList = productQueryService.getAllProducts();
        populateDataRows(sheet, productList);
        log.info("PM (GeneratePmReport): Retrieved {} products from database for report generation.)", productList.size());
        ExcelReporterHelper.writeWorkbookToResponse(response, workbook);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveProduct(ProductsForPM product) {
        try {
            pmRepository.save(product);
            log.info("PM (saveProduct): Successfully saved/updated product with ID {}", product.getProductID());
        } catch (DataAccessException da) {
            log.error("PM (saveProduct): Database error while saving product: {}", da.getMessage());
            throw new DatabaseException("Failed to save product", da);
        } catch (Exception e) {
            log.error("PM (saveProduct): Unexpected error while saving product: {}", e.getMessage());
            throw new ServiceException("Failed to save product", e);
        }
    }

    @Transactional(readOnly = true)
    public String generateProductId() {
        Optional<String> lastIdOpt = pmRepository.getLastId();
        if (lastIdOpt.isPresent()) {
            String lastId = lastIdOpt.get();
            int lastNumber = Integer.parseInt(lastId.substring(3));
            return String.format("PRD%03d", lastNumber + 1);
        }
        return "PRD001";
    }
}







