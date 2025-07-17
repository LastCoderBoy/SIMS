package com.JK.SIMS.service.productManagement_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductManagementDTO;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;
import org.apache.poi.xssf.usermodel.XSSFRow;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.GlobalServiceHelper.amongInvalidStatus;
import static com.JK.SIMS.service.productManagement_service.PMServiceHelper.*;

@Service
public class ProductManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ProductManagementService.class);

    private final PM_repository pmRepository;
    private final InventoryControlService icService;
    private final IC_repository icRepository;

    @Autowired
    public ProductManagementService(PM_repository pmRepository, InventoryControlService icService, IC_repository icRepository) {
        this.pmRepository = pmRepository;
        this.icService = icService;
        this.icRepository = icRepository;
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
    public PaginatedResponse<ProductManagementDTO> getAllProducts(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
            Page<ProductsForPM> allProducts = pmRepository.findAll(pageable);
            PaginatedResponse<ProductManagementDTO> dtoPaginatedResponse = transformToDTOPaginatedResponse(allProducts);
            logger.info("PM (getAllProducts): Retrieved {} products from database.", allProducts.getTotalElements());
            return dtoPaginatedResponse;
        } catch (DataAccessException e) {
            throw new DatabaseException("PM (getAllProducts): Failed to retrieve products from database", e);
        } catch (Exception e) {
            throw new ServiceException("PM (getAllProducts): Failed to retrieve products", e);
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
    public List<ProductManagementDTO> getAllProducts() {
        List<ProductsForPM> productList = pmRepository.findAll(Sort.by("productID").ascending());
        return productList.stream().map(this::convertToDTO).toList();
    }

    
    /**
     * Adds a new product to the system after validation and generates a new product ID.
     * If the product status is not in PLANNING and ARCHIVED, it will also be added to IC section.
     *
     * @param newProduct The product object containing all required product details
     * @return ApiResponse object containing success status and message
     * @throws ValidationException   if product details are invalid
     * @throws ServiceException      if there's an error during product addition
     */
    @Transactional
    public ApiResponse addProduct(ProductsForPM newProduct){
        try {
            if (validateProduct(newProduct)) {
                String newID = generateProductId();
                newProduct.setProductID(newID);
                pmRepository.save(newProduct);
                if (!newProduct.getStatus().equals(ProductStatus.PLANNING)) {
                    icService.addProduct(newProduct, false);
                }
                logger.info("PM (addProduct): New product added: ID = {}, Name = {}", newID, newProduct.getName());
                return new ApiResponse(true, "PM: Product added successfully with ID: " + newID);
            }
            throw new ValidationException("PM (addProduct): Invalid product details");
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ServiceException("PM (addProduct): Failed to add product ", e);
        }
    }

    /**
     * Deletes a product from both Product Management and Inventory Control systems.
     * This is a cascading delete operation that removes the product from both PM and IC databases.
     *
     * @param id The unique identifier of the product to be deleted
     * @return ResponseEntity containing success message if deletion is successful
     * @throws BadRequestException if the product with given ID is not found
     * @throws DatabaseException if there's an error during database operation
     * @throws ServiceException if any other error occurs during deletion
     */
    @Transactional
    public ResponseEntity<?> deleteProduct(String id) throws BadRequestException {
        try {
            Optional<ProductsForPM> productNeedsToBeDeleted = pmRepository.findById(id);
            if (productNeedsToBeDeleted.isPresent()) {
                icRepository.deleteByProduct_ProductID(id);
                pmRepository.delete(productNeedsToBeDeleted.get());
                logger.info("PM (deleteProduct): Product with ID {} is deleted", id);
                return ResponseEntity.ok("Product with ID " + id + " is deleted successfully!");
            }
            throw new BadRequestException("PM (deleteProduct): Product with ID " + id + " not found");
        } catch (BadRequestException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new DatabaseException("PM (deleteProduct): Database error occurred.", e);
        } catch (Exception e) {
            throw new ServiceException("PM (deleteProduct): Failed to delete product with ID " + id, e);
        }
    }


    /**
     * Updates an existing product with new information. Only non-null fields from the newProduct
     * will be used to update the existing product.
     *
     * @param productId  The unique identifier of the product to update
     * @param newProduct The product object containing the new values to update
     * @return ApiResponse indicating success or failure of the update operation
     * @throws ResourceNotFoundException if the product with given ID is not found
     * @throws ValidationException if the new location format is invalid
     * @throws ServiceException    if any other error occurs during update
     */
    public ApiResponse updateProduct(String productId, ProductsForPM newProduct) {
        try {
            ProductsForPM currentProduct = findProductById(productId);

            if(isAllFieldsNull(newProduct)){
                logger.info("PM (updateProduct): No fields to update. Product with ID {} not updated.", productId);
                return new ApiResponse(false, "Missing fields to update.");
            }

            updateProductFields(currentProduct, newProduct);
            updateProductStatus(currentProduct, newProduct, productId);
            updateProductLocation(currentProduct, newProduct);

            pmRepository.save(currentProduct);
            logger.info("PM (updateProduct): Product with ID {} updated successfully", productId);

            return new ApiResponse(true, "Product with ID " + productId + " updated successfully!");
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("PM (updateProduct): Product with ID " + productId + " not found");
        } catch (ValidationException e) {
            throw new ValidationException(e.getMessage());
        } catch (Exception e) {
            throw new ServiceException("PM (updateProduct): Internal error " + productId, e);
        }
    }

    private void updateProductFields(ProductsForPM currentProduct, ProductsForPM newProduct) {
        if (newProduct.getName() != null) {
            currentProduct.setName(newProduct.getName());
        }
        if (newProduct.getCategory() != null) {
            currentProduct.setCategory(newProduct.getCategory());
        }
        if (newProduct.getPrice() != null) {
            currentProduct.setPrice(newProduct.getPrice());
        }
    }

    private void updateProductStatus(ProductsForPM currentProduct, ProductsForPM newProduct, String productId) {
        if (newProduct.getStatus() != null) {
            Optional<InventoryData> productInIC = icRepository.findByPmProduct_ProductID(productId);
            ProductStatus previousStatus = currentProduct.getStatus();

            if (validateStatusBeforeAdding(currentProduct, newProduct)) {
                currentProduct.setStatus(newProduct.getStatus());
                handleStatusChange(currentProduct, newProduct, previousStatus, productInIC);
            } else {
                currentProduct.setStatus(newProduct.getStatus());
                if (amongInvalidStatus(newProduct.getStatus())) {
                    updateInventoryDataStatus(productInIC, InventoryDataStatus.INVALID);
                }else{
                    if(productInIC.isPresent()){
                        if(productInIC.get().getCurrentStock() <= productInIC.get().getMinLevel()){
                            updateInventoryDataStatus(productInIC, InventoryDataStatus.LOW_STOCK);
                        }else {
                            updateInventoryDataStatus(productInIC, InventoryDataStatus.IN_STOCK);
                        }
                    }
                }
            }
        }
    }

    private void handleStatusChange(ProductsForPM currentProduct, ProductsForPM newProduct, ProductStatus previousStatus, Optional<InventoryData> productInIC) {
        if (newProduct.getStatus().equals(ProductStatus.ACTIVE) || newProduct.getStatus().equals(ProductStatus.ON_ORDER)) {
            if (previousStatus.equals(ProductStatus.ARCHIVED)) {
                if (productInIC.isEmpty()) {
                    icService.addProduct(currentProduct, false);
                }
            } else {
                icService.addProduct(currentProduct, false);
            }
        } else {
            // No need to add to IC, because the product is already present in the IC and we have to change to INVALID status
            updateInventoryDataStatus(productInIC, InventoryDataStatus.INVALID);
        }
    }

    private void updateInventoryDataStatus(Optional<InventoryData> productInIC, InventoryDataStatus status) {
        productInIC.ifPresent(icData -> {
            icData.setStatus(status);
            icRepository.save(icData);
        });
    }

    private void updateProductLocation(ProductsForPM currentProduct, ProductsForPM newProduct) {
        if (newProduct.getLocation() != null) {
            validateLocationFormat(newProduct.getLocation());
            currentProduct.setLocation(newProduct.getLocation());
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
    public PaginatedResponse<ProductManagementDTO> searchProduct(String text, int page, int size) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
                Page<ProductsForPM> result = pmRepository.searchProducts(text.trim().toLowerCase(), pageable);
                return transformToDTOPaginatedResponse(result);
            }
            logger.info("PM (searchProduct): No search text provided. Retrieving first page with default size.");
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
     * @param sortBy    Field to sort by (e.g., "productID", "name", "price")
     * @param direction Sort direction ("asc" or "desc")
     * @param page      Zero-based page number
     * @param size      Number of items per page
     * @return PaginatedResponse containing filtered and sorted ProductManagementDTO objects
     * @throws ValidationException if filter value is invalid
     * @throws DatabaseException   if database operation fails
     * @throws ServiceException    if any other error occurs
     */
    public PaginatedResponse<ProductManagementDTO> filterProducts(String filter, String sortBy, String direction, int page, int size) {
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

            return transformToDTOPaginatedResponse(resultPage);

        } catch (IllegalArgumentException iae) {
            logger.error("PM (filterProducts): Invalid filter value: {}", iae.getMessage());
            throw new ValidationException("PM (filterProducts): Invalid filter value");
        } catch (DataAccessException da) {
            throw new DatabaseException("PM (filterProducts): Database error", da);
        } catch (Exception e) {
            throw new ServiceException("PM (filterProducts): Failed to filter products", e);
        }
    }

    private ProductManagementDTO convertToDTO(ProductsForPM product) {
        return new ProductManagementDTO(
                product.getProductID(),
                product.getName(),
                product.getLocation(),
                product.getCategory(),
                product.getPrice(),
                product.getStatus()
        );
    }


    private PaginatedResponse<ProductManagementDTO> transformToDTOPaginatedResponse(Page<ProductsForPM> products) {
        PaginatedResponse<ProductManagementDTO> response = new PaginatedResponse<>();
        response.setContent(products.getContent().stream().map(this::convertToDTO).toList());
        response.setTotalPages(products.getTotalPages());
        response.setTotalElements(products.getTotalElements());
        return response;
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

    /**
     * Generates an Excel report containing product management data.
     * Creates a workbook with product details including ID, category, name,
     * location, price, and status.
     *
     * @param response HttpServletResponse to write the Excel file to
     * @param allProducts List of products to include in the report
     */
    public void generatePMReport(HttpServletResponse response, List<ProductManagementDTO> allProducts) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Product Management");
        createHeaderRow(sheet);
        populateDataRows(sheet, allProducts);
        writeWorkbookToResponse(response, workbook);
    }

    private void createHeaderRow(XSSFSheet sheet) {
        XSSFRow row = sheet.createRow(0);
        row.createCell(0).setCellValue("Product ID");
        row.createCell(1).setCellValue("Category");
        row.createCell(2).setCellValue("Name");
        row.createCell(3).setCellValue("Location");
        row.createCell(4).setCellValue("Price");
        row.createCell(5).setCellValue("Status");
    }

    private void populateDataRows(XSSFSheet sheet, List<ProductManagementDTO> allProducts) {
        int dataRowIndex = 1;
        for (ProductManagementDTO pm : allProducts) {
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(pm.getProductID());
            rowForData.createCell(1).setCellValue(pm.getCategory().toString());
            rowForData.createCell(2).setCellValue(pm.getName());
            rowForData.createCell(3).setCellValue(pm.getLocation());
            rowForData.createCell(4).setCellValue(pm.getPrice().doubleValue());
            rowForData.createCell(5).setCellValue(pm.getStatus().toString());
            dataRowIndex++;
        }
    }

    private void writeWorkbookToResponse(HttpServletResponse response, XSSFWorkbook workbook) {
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            logger.info("PM (generatePMReport): Product Management report is downloaded with {} data size", workbook.getNumberOfSheets());
            workbook.close();
        } catch (IOException e) {
            logger.error("PM (generatePMReport): Error writing Excel file", e);
        }
    }

    // Helper method for internal use
    @Transactional(readOnly = true)
    public ProductsForPM findProductById(String productId) {
        return pmRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("PM (updateProduct): Product with ID " + productId + " not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveProduct(ProductsForPM product) {
        try {
            pmRepository.save(product);
            logger.info("PM (saveProduct): Successfully saved/updated product with ID {}",
                    product.getProductID());
        } catch (DataAccessException da) {
            logger.error("PM (saveProduct): Database error while saving product: {}",
                    da.getMessage());
            throw new DatabaseException("Failed to save product", da);
        } catch (Exception e) {
            logger.error("PM (saveProduct): Unexpected error while saving product: {}",
                    e.getMessage());
            throw new ServiceException("Failed to save product", e);
        }
    }
}









