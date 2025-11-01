package com.JK.SIMS.service.productManagementService;

import com.JK.SIMS.config.security.JWTService;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductManagementDTO;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.utilities.ExcelReporterHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.productManagementService.PMServiceHelper.*;
import static com.JK.SIMS.service.utilities.GlobalServiceHelper.amongInvalidStatus;

@Service
@Slf4j
public class ProductManagementService {

    private final PM_repository pmRepository;
    private final InventoryControlService icService;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final JWTService jwtService;

    @Autowired
    public ProductManagementService(PM_repository pmRepository, InventoryControlService icService, InventoryServiceHelper inventoryServiceHelper, JWTService jwtService) {
        this.pmRepository = pmRepository;
        this.icService = icService;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.jwtService = jwtService;
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
            log.info("PM (getAllProducts): Retrieved {} products from database.", allProducts.getTotalElements());
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
     * If the product status is NOT Equal to PLANNING, it will be added to IC section.
     *
     * @param newProduct The product object containing all required product details
     * @return ApiResponse object containing success status and message
     * @throws ValidationException   if product details are invalid
     * @throws ServiceException      if there's an error during product addition
     */
    @Transactional
    public ApiResponse<Void> addProduct(ProductsForPM newProduct){
        try {
            if (validateProduct(newProduct)) {
                String newID = generateProductId();
                newProduct.setProductID(newID);
                pmRepository.save(newProduct);
                if (!newProduct.getStatus().equals(ProductStatus.PLANNING)) {
                    icService.addProduct(newProduct, false);
                }
                log.info("PM (addProduct): New product added: ID = {}, Name = {}", newID, newProduct.getName());
                return new ApiResponse<>(true, "PM: Product added successfully with ID: " + newID);
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
    public ApiResponse<Void> deleteProduct(String id, String jwtToken) throws BadRequestException {
        try {
            Optional<ProductsForPM> productNeedsToBeDeleted = pmRepository.findById(id);
            String username = jwtService.extractUsername(jwtToken);
            if (productNeedsToBeDeleted.isPresent()) {
                icService.deleteByProductId(id); // Delete first from the Inventory as they have a connection with each other.
                pmRepository.delete(productNeedsToBeDeleted.get());
                log.info("PM (deleteProduct): Product with ID {} is deleted successfully by {}.", id, username);
                return new ApiResponse<>(true, "Product with ID " + id + " deleted successfully!");
            }
            log.info("PM (deleteProduct): Attempt for deletion product with ID {} by {} is failed.", id, username);
            log.info("PM (deleteProduct): Product with ID {} not found", id);
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
    @Transactional
    public ApiResponse<Void> updateProduct(String productId, ProductsForPM newProduct) {
        try {
            ProductsForPM currentProduct = pmRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("PM (updateProduct): Product with ID " + productId + " not found"));

            if(isAllFieldsNull(newProduct)){
                log.info("PM (updateProduct): No fields to update. Product with ID {} not updated.", productId);
                return new ApiResponse<>(false, "Missing fields to update.");
            }

            updateProductFields(currentProduct, newProduct);
            updateProductAndInventoryStatus(currentProduct, newProduct, productId);
            updateProductLocation(currentProduct, newProduct);

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

    private void updateProductAndInventoryStatus(ProductsForPM currentProduct, ProductsForPM newProduct, String productId) {
        if (newProduct.getStatus() != null && !newProduct.getStatus().equals(currentProduct.getStatus())) {
            Optional<InventoryControlData> productInIcOpt =
                    inventoryServiceHelper.getInventoryProductByProductId(productId);
            ProductStatus previousStatus = currentProduct.getStatus();

            if (validateStatusBeforeAdding(currentProduct, newProduct)) {
                currentProduct.setStatus(newProduct.getStatus());
                handleStatusChange(currentProduct, newProduct, previousStatus, productInIcOpt);
            } else {
                currentProduct.setStatus(newProduct.getStatus());
                if (amongInvalidStatus(newProduct.getStatus())) {
                    icService.updateInventoryStatus(productInIcOpt, InventoryDataStatus.INVALID);
                }
            }
        }
    }

    private void handleStatusChange(ProductsForPM currentProduct, ProductsForPM newProduct, ProductStatus previousStatus, Optional<InventoryControlData> productInIcOpt) {
        if (newProduct.getStatus().equals(ProductStatus.ACTIVE) || newProduct.getStatus().equals(ProductStatus.ON_ORDER)) {
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
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductManagementDTO> searchProduct(String text, int page, int size) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
                Page<ProductsForPM> result = pmRepository.searchProducts(text.trim().toLowerCase(), pageable);
                return transformToDTOPaginatedResponse(result);
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
     * @param sortBy    Field to sort by (e.g., "productID", "name", "price")
     * @param direction Sort direction ("asc" or "desc")
     * @param page      Zero-based page number
     * @param size      Number of items per page
     * @return PaginatedResponse containing filtered and sorted ProductManagementDTO objects
     * @throws ValidationException if filter value is invalid
     * @throws DatabaseException   if database operation fails
     * @throws ServiceException    if any other error occurs
     */
    @Transactional(readOnly = true)
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
     * Generates an Excel report containing product management data.
     * Creates a workbook with product details including ID, category, name,
     * location, price, and status.
     *
     * @param response HttpServletResponse to write the Excel file to
     */
    public void generatePMReport(HttpServletResponse response) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Product Management");
        List<ProductManagementDTO> allProducts = getAllProducts();
        createHeaderRow(sheet);
        populateDataRows(sheet, allProducts);
        log.info("PM (GeneratePmReport): Retrieved {} products from database for report generation.)", allProducts.size());
        ExcelReporterHelper.writeWorkbookToResponse(response, workbook);
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
}









