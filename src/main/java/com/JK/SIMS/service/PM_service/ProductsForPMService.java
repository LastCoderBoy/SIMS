package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.PM_repo.PM_repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

}
