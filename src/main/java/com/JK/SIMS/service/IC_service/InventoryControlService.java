package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.InventoryException;
import com.JK.SIMS.models.IC_models.InventoryControlStatus;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import org.hibernate.service.NullServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryControlService {
    private static Logger logger = LoggerFactory.getLogger(InventoryControlService.class);

    private final IC_repository icRepository;
    @Autowired
    public InventoryControlService(IC_repository icRepository) {
        this.icRepository = icRepository;
    }

    public ResponseEntity<List<InventoryDataResponse>> getAllInventoryProducts() {
        try {
            List<InventoryData> inventoryDataList = icRepository.findAll();
            List<InventoryDataResponse> responseList = inventoryDataList.stream()
                    .map(inventoryData -> {
                        InventoryDataResponse response = new InventoryDataResponse();
                        response.setSKU(inventoryData.getSKU());
                        response.setProductName(inventoryData.getProduct().getName());
                        response.setLocation(inventoryData.getProduct().getLocation());
                        response.setStatus(inventoryData.getStatus());
                        response.setCurrentStock(inventoryData.getCurrentStock());
                        response.setMinLevel(inventoryData.getMinLevel());
                        response.setLastUpdate(inventoryData.getLastUpdate());
                        return response;
                    }).toList();

            logger.info("IC: Retrieved {} products from database.", inventoryDataList.size());
            return new ResponseEntity<>(responseList, HttpStatus.OK);
        }
        catch (Exception e){
            logger.error("IC: Failed to retrieve products", e);
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.BAD_REQUEST);
        }
    }

/** {@code CurrentStock} & {@code MinLevel} set to 0 and {@code Status} to OUT_STOCK intentionally when the PM.addProduct() method is called.
* because the IC section will handle updates to these values later (when stock is received or ordered)
*/
    public void addProduct(ProductsForPM product){
        try {
            InventoryData inventoryData = new InventoryData();
            String sku = generateSKU(product.getProductID(), product.getCategory());
            inventoryData.setSKU(sku);
            inventoryData.setProduct(product);
            inventoryData.setLocation(product.getLocation());
            inventoryData.setCurrentStock(0);
            inventoryData.setLastUpdate(LocalDateTime.now());
            inventoryData.setMinLevel(0);
            inventoryData.setStatus(InventoryControlStatus.OUT_STOCK);
            icRepository.save(inventoryData);
            logger.info("IC: New product is added with the {} SKU", sku);
        }catch (DataAccessException da){
            logger.error("IC: Database error while adding product with productID {}: {}",
                    product.getProductID(), da.getMessage());
            throw new InventoryException("IC: Failed to save inventory data due to database error", da);
        }catch (InventoryException ex){
            throw ex; // Redirects to the Global Exception Handler
        }
        catch (Exception e){
            logger.error("IC: Unexpected error while adding product with productID {}: {}",
                    product.getProductID(), e.getMessage());
            throw new InventoryException("IC: Unexpected error occurred while adding product", e);
        }
    }

    /**
     * Our SKU will be: 3 letters from {@code Category} slash and last digits from the {@code productID}
     * @return EDU-001 as an example.
     */
    private String generateSKU(String productID, ProductCategories category){
        try {
            if (productID == null || productID.length() < 4) {
                throw new IllegalArgumentException("Product ID must be at least 4 characters (e.g., PRD001)");
            }
            if (category == null) {
                throw new IllegalArgumentException("Category cannot be null");
            }

            String lastDigits = productID.substring(3);
            String categoryStart = category.toString().substring(0, 3);
            return categoryStart + "-" + lastDigits;
        } catch (IllegalArgumentException iae) {
            logger.error("IC: Invalid input for SKU generation - productID: {}, category: {}: {}",
                    productID, category, iae.getMessage());
            throw new InventoryException("IC: Invalid product ID or category for SKU generation", iae);
        } catch (StringIndexOutOfBoundsException sioobe) {
            logger.error("IC: SKU generation failed due to malformed productID {} or category {}: {}",
                    productID, category, sioobe.getMessage());
            throw new InventoryException("IC: Malformed product ID or category for SKU generation", sioobe);
        }
    }
}
