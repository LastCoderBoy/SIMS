package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.models.IC_models.InventoryControlStatus;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataResponse;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    public void addProduct(ProductsForPM product){
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
        logger.info("IC: New product is added with the {} SKU" , sku);
    }


    private String generateSKU(String productID, ProductCategories category){
        // Our SKU will be: 3 letters from Category slash and last digits from the productID
        // Example: EDU-001
        String lastDigits = productID.substring(3);
        String categoryStart = category.toString().substring(0, 3);
        return categoryStart + "-" + lastDigits;
    }
}
