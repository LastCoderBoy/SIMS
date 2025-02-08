package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.ProductCategories;
import com.JK.SIMS.models.ProductStatus;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

import static com.JK.SIMS.service.PM_service.PMServiceHelper.validateNewProduct;

@Service
public class ProductsForPMService {

    private static Logger logger = LoggerFactory.getLogger(ProductsForPMService.class);

    private final PM_repository pmRepository;
    @Autowired
    public ProductsForPMService(PM_repository pmRepository) {
        this.pmRepository = pmRepository;
    }


    public List<ProductsForPM> getAllProducts() {
        try {
            List<ProductsForPM> allProducts = pmRepository.findAll();
            logger.info("Retrieved {} products from database.", allProducts.size());
            return allProducts;
        } catch (Exception e) {
            logger.error("Failed to retrieve products", e);
            return Collections.emptyList();
        }
    }

    public ResponseEntity<String> addProduct(ProductsForPM newProduct) {
        if (newProduct == null) {
            return new ResponseEntity<>("Product cannot be null", HttpStatus.BAD_REQUEST);
        }

        try {
            validateNewProduct(newProduct);
        } catch (ValidationException e) {
            logger.warn("Product validation failed: {}", e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        try {
            String newID = generateProductId();
            newProduct.setProductID(newID);
            if(newProduct.getStock() != 0)
                newProduct.setStatus(ProductStatus.IN_STOCK);
            else
                newProduct.setStatus(ProductStatus.OUT_STOCK);
            pmRepository.save(newProduct);

            logger.info("New product added: ID = {}, Name = {}", newID, newProduct.getName());
            return ResponseEntity.ok("Product added successfully with ID: " + newID);

        } catch (DuplicateKeyException e) {
            logger.error("Duplicate product ID detected", e);
            // Retry once with a new ID
            return retryProductCreation(newProduct);
        } catch (Exception e) {
            logger.error("Failed to add product", e);
            return new ResponseEntity<>("Failed to add product due to internal error",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> deleteProduct(String id) {
        try {
            Optional<ProductsForPM> productNeedsToBeDeleted = pmRepository.findById(id);
            if (productNeedsToBeDeleted.isPresent()) {
                pmRepository.delete(productNeedsToBeDeleted.get());
                logger.info("Product with ID {} is deleted", id);
                return ResponseEntity.ok("Product with ID " + id + " is deleted successfully!");
            } else {
                logger.warn("Product with ID {} not found", id);
                return new ResponseEntity<>("Product with ID " + id + " not found", HttpStatus.NOT_FOUND);
            }
        } catch (DataAccessException e) {
            logger.error("Deletion failed for product with ID {}", id, e);
            return new ResponseEntity<>("Deletion failed due to a database error", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Unexpected error occurred while deleting product with ID {}", id, e);
            return new ResponseEntity<>("Unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<String> updateProduct(String productId, ProductsForPM newProduct) {
        if (productId == null || productId.trim().isEmpty()) {
            return new ResponseEntity<>("Product ID cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (newProduct == null) {
            return new ResponseEntity<>("Product data cannot be null", HttpStatus.BAD_REQUEST);
        }

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
                        if (newProduct.getStock() != null) {
                            existingProduct.setStock(newProduct.getStock());
                        }

                        ProductsForPM savedProduct = pmRepository.save(existingProduct);
                        logger.info("Product with ID {} updated successfully", productId);
                        return ResponseEntity.ok("Product " + savedProduct.getProductID() + " is Updated Successfully");
                    })
                    .orElseGet(() -> {
                        logger.warn("Product with ID {} not found", productId);
                        return new ResponseEntity<>("Product not found with ID: " + productId, HttpStatus.NOT_FOUND);
                    });
        } catch (Exception e) {
            logger.error("Error updating product {}: ", productId, e);
            return new ResponseEntity<>("An error occurred while updating the product.", HttpStatus.INTERNAL_SERVER_ERROR);
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

    private ResponseEntity<String> retryProductCreation(ProductsForPM product) {
        int retryAttempts = 3;

        for (int i = 0; i < retryAttempts; i++) {
            try {
                String newID;
                do {
                    newID = generateProductId();
                } while (pmRepository.existsById(newID));

                product.setProductID(newID);
                pmRepository.save(product);

                logger.info("Product added on retry: ID = {}, Name = {}", newID, product.getName());
                return ResponseEntity.ok("Product added successfully with ID: " + newID);

            } catch (Exception e) {
                logger.error("Retry failed when adding product, attempt {}/{}", i + 1, retryAttempts, e);
            }
        }

        return new ResponseEntity<>("Failed to add product after retries",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }


    public ResponseEntity<List<ProductsForPM>> searchProduct(String text){
        if(text != null && !text.trim().isEmpty()){
            Optional<List<ProductsForPM>> result = pmRepository.searchByProductIDAndCategoryAndName(text);
            return result.map(productsForPMS -> new ResponseEntity<>(productsForPMS, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK));
        }
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.BAD_REQUEST);
    }

    public void generatePMReport(HttpServletResponse response, List<ProductsForPM> allProducts) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Product Management");
        XSSFRow row = sheet.createRow(0);

        row.createCell(0).setCellValue("Product ID");
        row.createCell(1).setCellValue("Category");
        row.createCell(2).setCellValue("Name");
        row.createCell(3).setCellValue("Price");
        row.createCell(4).setCellValue("Stock");
        row.createCell(5).setCellValue("Status");

        int dataRowIndex = 1;
        for(ProductsForPM pm : allProducts){
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(pm.getProductID());
            rowForData.createCell(1).setCellValue(pm.getCategory().toString());
            rowForData.createCell(2).setCellValue(pm.getName());
            rowForData.createCell(3).setCellValue(pm.getPrice().doubleValue());
            rowForData.createCell(4).setCellValue(pm.getStock());
            rowForData.createCell(5).setCellValue(pm.getStatus().toString());
            dataRowIndex++;
        }

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            logger.info("Product Management report is downloaded with {} data size", allProducts.size());
            workbook.close();
        } catch (IOException e) {
            logger.error("Error writing Excel file", e);
        }
    }

    private static final Set<String> VALID_SORT_OPTIONS = Set.of("lowtohigh", "hightolow");

    public ResponseEntity<List<ProductsForPM>> filterProducts(String category, String sortBy, String status) {
        try {
            // Validate inputs first
            validateInputs(category, sortBy, status);

            // Get filtered products
            List<ProductsForPM> products = getFilteredProducts(category, status);

            // Apply sorting if requested
            if (sortBy != null) {
                sortProducts(products, sortBy.toLowerCase().trim());
            }

            return products.isEmpty() ? new ResponseEntity<>(HttpStatus.NO_CONTENT) : ResponseEntity.ok(products);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid input parameters: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (DataAccessException e) {
            logger.error("Database error while filtering products: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Unexpected error while filtering products: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    private void validateInputs(String category, String sortBy, String status) {
        if (category != null) {
            try {
                ProductCategories.valueOf(category);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid category: " + category);
            }
        }

        if (status != null) {
            try {
                ProductStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
        }

        if (sortBy != null && !VALID_SORT_OPTIONS.contains(sortBy.toLowerCase().trim())) {
            throw new IllegalArgumentException("Invalid sortBy parameter: " + sortBy);
        }
    }

    private List<ProductsForPM> getFilteredProducts(String category, String status) {
        List<ProductsForPM> products;

        if (category != null) {
            products = pmRepository.findAllByCategory(ProductCategories.valueOf(category));
            logger.info("Found {} products for category {}", products.size(), category);
        } else if (status != null) {
            products = pmRepository.findAllByStatus(ProductStatus.valueOf(status));
            logger.info("Found {} products for status {}", products.size(), status);
        } else {
            products = pmRepository.findAll();
            logger.info("Retrieved all {} products", products.size());
        }

        return products;
    }

    private void sortProducts(List<ProductsForPM> products, String sortBy) {
        switch (sortBy) {
            case "lowtohigh":
                products.sort(Comparator.comparing(ProductsForPM::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                logger.info("Sorted {} products by price (ascending)", products.size());
                break;
            case "hightolow":
                products.sort(Comparator.comparing(ProductsForPM::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                logger.info("Sorted {} products by price (descending)", products.size());
                break;
        }
    }
}









