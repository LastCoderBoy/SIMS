package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.LowStockService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/products/inventory/low-stock")
public class LowStockController {
    private static final Logger logger = LoggerFactory.getLogger(LowStockController.class);

    private final LowStockService lowStockService;
    @Autowired
    public LowStockController(LowStockService lowStockService) {
        this.lowStockService = lowStockService;
    }

    // Get All
    @GetMapping
    public ResponseEntity<?> getAllLowStockRecords(@RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                                   @RequestParam(defaultValue = "asc") String sortDirection,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size){
        logger.info("LowStockController: getAllPaginatedLowStockRecords() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> response =
                lowStockService.getAllPaginatedLowStockRecords(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(@RequestParam(required = false) String text,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size){
        logger.info("LowStockController: searchProduct() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> inventoryResponse = lowStockService.searchInLowStockProducts(text, page, size);
        return ResponseEntity.ok(inventoryResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(@RequestParam(required = false) ProductCategories category,
                                            @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                            @RequestParam(defaultValue = "asc") String sortDirection,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size){
        logger.info("LowStockController: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> filterResponse =
                lowStockService.filterLowStockProducts(category, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filterResponse);
    }

    @GetMapping("/report")
    public void generateLowStockReport(HttpServletResponse response,
                                       @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                       @RequestParam(defaultValue = "asc") String sortDirection){
        logger.info("LowStockController: generateLowStockReport() calling...");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=LowStockReport.xlsx";
        response.setHeader(headerKey, headerValue);
        lowStockService.generateLowStockReport(response, sortBy, sortDirection);
    }
}
