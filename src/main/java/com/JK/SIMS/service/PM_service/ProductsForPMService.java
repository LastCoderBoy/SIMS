package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import com.JK.SIMS.service.IC_service.InventoryControlService;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.PM_service.PMServiceHelper.*;

@Service
public class ProductsForPMService {

    private static final Logger logger = LoggerFactory.getLogger(ProductsForPMService.class);

    private final PM_repository pmRepository;
    private final InventoryControlService icService;
    private final IC_repository icRepository;
    @Autowired
    public ProductsForPMService(PM_repository pmRepository, InventoryControlService icService, IC_repository icRepository) {
        this.pmRepository = pmRepository;
        this.icService = icService;
        this.icRepository = icRepository;
    }


    public List<ProductsForPM> getAllProducts() {
        try {
            List<ProductsForPM> allProducts = pmRepository.findAll();
            logger.info("PM (getAllProducts): Retrieved {} products from database.", allProducts.size());
            return allProducts;
        }
        catch (DataAccessException e) {
            throw new DatabaseException("PM (getAllProducts): Failed to retrieve products from database", e);
        } catch (Exception e) {
            throw new ServiceException("PM (getAllProducts): Failed to retrieve products", e);
        }

    }

    public ApiResponse addProduct(ProductsForPM newProduct, boolean hasAdminAccess) throws AccessDeniedException {
        try {
            if(hasAdminAccess) {
                String newID = null;
                if (validateNewProduct(newProduct)) {
                    newID = generateProductId();
                    newProduct.setProductID(newID);
                    pmRepository.save(newProduct);
                }
                // Adding directly to the IC if the product status is not in COMING SOON
                if (!newProduct.getStatus().equals(ProductStatus.COMING_SOON)) {
                    icService.addProduct(newProduct);
                }

                logger.info("PM: New product added: ID = {}, Name = {}", newID, newProduct.getName());
                return new ApiResponse(true, "PM: Product added successfully with ID: " + newID);
            }
            throw new AccessDeniedException("PM (addProduct): Forbidden access");
        }
        catch (ValidationException | AccessDeniedException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ServiceException("PM (addProduct): Failed to add product ", e);
        }
    }

    public ResponseEntity<?> deleteProduct(String id) throws BadRequestException {
        try {
            Optional<ProductsForPM> productNeedsToBeDeleted = pmRepository.findById(id);

            if (productNeedsToBeDeleted.isPresent()) {
                // Delete all related records first from IC table
                icRepository.deleteByProduct_ProductID(id);

                //Then delete the record from PM table
                pmRepository.delete(productNeedsToBeDeleted.get());
                logger.info("PM: Product with ID {} is deleted", id);
                return ResponseEntity.ok("Product with ID " + id + " is deleted successfully!");
            }
            throw new BadRequestException("PM (deleteProduct): Product with ID " + id + " not found");
        }
        catch(BadRequestException e) {
            throw e;
        }
        catch (DataAccessException e) {
            throw new DatabaseException("PM (deleteProduct): Failed to delete product with ID " + id, e);
        } catch (Exception e) {
            throw new ServiceException("PM (deleteProduct): Failed to delete product with ID " + id, e);
        }
    }

    public ResponseEntity<?> updateProduct(String productId, ProductsForPM newProduct) throws BadRequestException {
        try {
            return pmRepository.findById(productId)
                    .map(existingProduct -> {
                        // Update only the non-null fields
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
                            existingProduct.setStatus(newProduct.getStatus());
                        }
                        if (newProduct.getLocation() != null) {
                            existingProduct.setLocation(newProduct.getLocation());
                        }

                        pmRepository.save(existingProduct);
                        logger.info("PM (updateProduct): Product with ID {} updated successfully", productId);
                        return ResponseEntity.ok(new ApiResponse(true, "Product with ID " + productId + " updated successfully!"));
                    })
                    .orElseThrow(() -> new BadRequestException("PM (updateProduct): Product with ID " + productId + " not found"));
        }
        catch (BadRequestException e) {
            throw e;
        }
        catch (ValidationException e) {
            throw new ValidationException("PM (updateProduct): Invalid product data : " + e.getMessage());
        }
        catch (Exception e) {
            throw new ServiceException("PM (updateProduct): Internal error " + productId, e);
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

    public ResponseEntity<List<ProductsForPM>> searchProduct(String text){
        if(text != null && !text.trim().isEmpty()){

            Optional<List<ProductsForPM>> result = pmRepository.searchByProductIDAndCategoryAndNameAndLocation(text);
            return result.map(
                    productsForPMS ->{
                            logger.info("PM (searchProduct): Retrieved {} data from the database.", productsForPMS.size());
                            return new ResponseEntity<>(productsForPMS, HttpStatus.OK);
                    })
                    .orElseThrow(() -> new DatabaseException("PM (searchProduct): Failed to filter the product!)"));
        }
        try {
            logger.info("PM (searchProduct): No search text provided. Retrieving all products.");
            return new ResponseEntity<>(getAllProducts(), HttpStatus.OK);
        }
        catch (Exception e){
            throw new ServiceException("PM (searchProduct): Failed to retrieve products", e);
        }
    }

    public ResponseEntity<List<ProductsForPM>> filterProducts(String category, String sortBy, String status) {
        try {
            // Validate inputs first
            validateInputs(category, sortBy, status);

            // Get filtered products
            List<ProductsForPM> products = getFilteredProducts(category, status);
            logger.info("PM (filterProducts): Retrieved {} products", products.size());

            // Apply sorting if requested
            if (sortBy != null) {
                sortProducts(products, sortBy.toLowerCase().trim());
            }
            return products.isEmpty() ? new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK) : ResponseEntity.ok(products);

        }
        catch (ValidationException e) {
            throw new ValidationException("PM (filterProducts): Invalid input: " + e.getMessage());
        }
        catch (DataAccessException e) {
            throw new DatabaseException("PM (filterProducts): Database error while filtering products: ", e);
        }
        catch (Exception e) {
            throw new ServiceException("PM (filterProducts): Internal error while filtering products: ", e.getCause());
        }
    }

    public void generatePMReport(HttpServletResponse response, List<ProductsForPM> allProducts){
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Product Management");
        XSSFRow row = sheet.createRow(0);

        row.createCell(0).setCellValue("Product ID");
        row.createCell(1).setCellValue("Category");
        row.createCell(2).setCellValue("Name");
        row.createCell(3).setCellValue("Location");
        row.createCell(4).setCellValue("Price");
        row.createCell(5).setCellValue("Status");

        int dataRowIndex = 1;
        for(ProductsForPM pm : allProducts){
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(pm.getProductID());
            rowForData.createCell(1).setCellValue(pm.getCategory().toString());
            rowForData.createCell(2).setCellValue(pm.getName());
            rowForData.createCell(3).setCellValue(pm.getLocation());
            rowForData.createCell(4).setCellValue(pm.getPrice().doubleValue());
            rowForData.createCell(5).setCellValue(pm.getStatus().toString());
            dataRowIndex++;
        }

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            logger.info("PM (generatePMReport): Product Management report is downloaded with {} data size", allProducts.size());
            workbook.close();
        } catch (IOException e) {
            logger.error("PM (generatePMReport): Error writing Excel file", e);
        }
    }


    private List<ProductsForPM> getFilteredProducts(String category, String status) {
        try {
            ProductCategories categoryEnum = (category != null) ? ProductCategories.valueOf(category.toUpperCase().trim()) : null;

            ProductStatus statusEnum = (status != null) ? ProductStatus.valueOf(status.toUpperCase().trim()) : null;

            return pmRepository.findByFilters(categoryEnum, statusEnum);
        } catch (Exception e) {
            throw new ServiceException("PM (getFilteredProducts): Internal Service Error", e);
        }
    }

}









