package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductManagementDTO;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import com.JK.SIMS.service.IC_service.InventoryControlService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.JK.SIMS.service.PM_service.PMServiceHelper.*;

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

    public Page<ProductManagementDTO> getAllProducts(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
            Page<ProductsForPM> allProducts = pmRepository.findAll(pageable);
            Page<ProductManagementDTO> resultList = allProducts.map(this::convertToDTO);
            logger.info("PM (getAllProducts): Retrieved {} products from database.", allProducts.getTotalElements());
            return resultList;
        } catch (DataAccessException e) {
            throw new DatabaseException("PM (getAllProducts): Failed to retrieve products from database", e);
        } catch (Exception e) {
            throw new ServiceException("PM (getAllProducts): Failed to retrieve products", e);
        }
    }

    public List<ProductManagementDTO> getAllProducts() {
        List<ProductsForPM> productList = pmRepository.findAll(Sort.by("productID").ascending());
        return productList.stream().map(this::convertToDTO).toList();
    }

    public ApiResponse addProduct(ProductsForPM newProduct, boolean hasAccess) throws AccessDeniedException {
        try {
            if (hasAccess) {
                if (validateNewProduct(newProduct)) {
                    String newID = generateProductId();
                    newProduct.setProductID(newID);
                    pmRepository.save(newProduct);
                    if (!newProduct.getStatus().equals(ProductStatus.PLANNING)) {
                        icService.addProduct(newProduct);
                    }
                    logger.info("PM: New product added: ID = {}, Name = {}", newID, newProduct.getName());
                    return new ApiResponse(true, "PM: Product added successfully with ID: " + newID);
                }
            }
            throw new AccessDeniedException("PM (addProduct): Forbidden access");
        } catch (ValidationException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("PM (addProduct): Failed to add product ", e);
        }
    }

    public ResponseEntity<?> deleteProduct(String id) throws BadRequestException {
        try {
            Optional<ProductsForPM> productNeedsToBeDeleted = pmRepository.findById(id);
            if (productNeedsToBeDeleted.isPresent()) {
                icRepository.deleteByProduct_ProductID(id);
                pmRepository.delete(productNeedsToBeDeleted.get());
                logger.info("PM: Product with ID {} is deleted", id);
                return ResponseEntity.ok("Product with ID " + id + " is deleted successfully!");
            }
            throw new BadRequestException("PM (deleteProduct): Product with ID " + id + " not found");
        } catch (BadRequestException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new DatabaseException("PM (deleteProduct): Failed to delete product with ID " + id, e);
        } catch (Exception e) {
            throw new ServiceException("PM (deleteProduct): Failed to delete product with ID " + id, e);
        }
    }

    public ResponseEntity<?> updateProduct(String productId, ProductsForPM newProduct) throws BadRequestException {
        try {
            return pmRepository.findById(productId)
                    .map(existingProduct -> {
                        updateExistingProduct(existingProduct, newProduct);
                        pmRepository.save(existingProduct);
                        logger.info("PM (updateProduct): Product with ID {} updated successfully", productId);
                        return ResponseEntity.ok(new ApiResponse(true, "Product with ID " + productId + " updated successfully!"));
                    })
                    .orElseThrow(() -> new BadRequestException("PM (updateProduct): Product with ID " + productId + " not found"));
        } catch (BadRequestException e) {
            throw e;
        } catch (ValidationException e) {
            throw new ValidationException(e.getMessage());
        } catch (Exception e) {
            throw new ServiceException("PM (updateProduct): Internal error " + productId, e);
        }
    }

    private void updateExistingProduct(ProductsForPM existingProduct, ProductsForPM newProduct) {
        if (newProduct.getName() != null) {
            existingProduct.setName(newProduct.getName());
        }
        if (newProduct.getCategory() != null) {
            existingProduct.setCategory(newProduct.getCategory());
        }
        if (newProduct.getPrice() != null) {
            existingProduct.setPrice(newProduct.getPrice());
        }
        if (newProduct.getStatus() != null) {
            if (existingProduct.getStatus().equals(ProductStatus.PLANNING) && !newProduct.getStatus().equals(ProductStatus.PLANNING)) {
                existingProduct.setStatus(newProduct.getStatus());
                icService.addProduct(existingProduct);
            }
            existingProduct.setStatus(newProduct.getStatus());
        }
        if (newProduct.getLocation() != null) {
            validateLocationFormat(newProduct.getLocation());
            existingProduct.setLocation(newProduct.getLocation());
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

    public Page<ProductManagementDTO> searchProduct(String text, int page, int size) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("productID").ascending());
                Page<ProductsForPM> result = pmRepository.searchProducts(text.trim().toLowerCase(), pageable);
                return result.map(this::convertToDTO);
            }
            logger.info("PM (searchProduct): No search text provided. Retrieving first page with default size.");
            return getAllProducts(0, 10);
        } catch (Exception e) {
            throw new ServiceException("PM (searchProduct): Failed to retrieve products", e);
        }
    }


    public Page<ProductManagementDTO> filterProducts(String filter, String sortBy, String direction, int page, int size) {
        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<ProductsForPM> resultPage;
            if(filter != null && !filter.trim().isEmpty()){
                String[] filterParts = filter.split(":");
                if(filterParts.length == 2){
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1].toLowerCase();
                    resultPage = switch (field) {
                        case "category" -> {
                            ProductCategories category = ProductCategories.valueOf(value);
                            yield pmRepository.findByCategory(category, pageable);
                        }
                        case "location" -> pmRepository.findByLocation(value, pageable);
                        case "price" -> pmRepository.findByPriceLevel(Integer.parseInt(value), pageable);
                        case "status" -> {
                            ProductStatus status = ProductStatus.valueOf(value);
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

            return resultPage.map(this::convertToDTO);

        } catch (IllegalArgumentException iae) {
            throw new ValidationException("PM (filterProducts): Invalid filter value: " + iae.getMessage());
        } catch (DataAccessException da) {
            throw new DatabaseException("PM (filterProducts): Database error", da);
        } catch (Exception e) {
            throw new ServiceException("PM (filterProducts): Failed to filter products", e);
        }
    }

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
}









