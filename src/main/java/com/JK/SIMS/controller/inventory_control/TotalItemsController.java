package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.TotalItemsService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/products/inventory/total")
public class TotalItemsController {
    private static final Logger logger = LoggerFactory.getLogger(TotalItemsController.class);

    private final TotalItemsService totalItemsService;
    @Autowired
    public TotalItemsController(TotalItemsService totalItemsService) {
        this.totalItemsService = totalItemsService;
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts(@RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                            @RequestParam(defaultValue = "asc") String sortDirection,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size){
        logger.info("TotalItemsController: getAllProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> inventoryResponse =
                totalItemsService.getPaginatedInventoryDto(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(inventoryResponse);
    }

    // Used to update the IC Stock levels.
    @PutMapping("/{sku}")
    public ResponseEntity<?> updateProduct(@PathVariable String sku,
                                           @RequestBody InventoryData newInventoryData) throws BadRequestException {
        if(sku == null || sku.trim().isEmpty() || newInventoryData == null){
            throw new BadRequestException("IC: updateProduct() SKU or new input body cannot be null or empty");
        }
        ApiResponse response = totalItemsService.updateProduct(sku.toUpperCase(), newInventoryData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(@RequestParam(required = false) String text,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size){
        logger.info("TotalItemsController: searchProduct() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> inventoryResponse = totalItemsService.searchProduct(text, page, size);
        return ResponseEntity.ok(inventoryResponse);
    }

    // Can filter by Stock Status, Product Category, and <= Stock Level
    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam String filterBy,
            @RequestParam(defaultValue = "pmProduct.name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("TotalItemsController: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> filterResponse =
                totalItemsService.filterProducts(filterBy, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filterResponse);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<?> deleteProduct(@PathVariable String sku) throws BadRequestException, AccessDeniedException {
        if(SecurityUtils.hasAccess()) {
            if(sku == null || sku.trim().isEmpty()){
                throw new BadRequestException("IC: deleteProduct() SKU cannot be empty");
            }
            logger.info("IC: deleteProduct() calling...");

            ApiResponse response = totalItemsService.deleteProduct(sku);
            return ResponseEntity.ok(response);
        }
        throw new AccessDeniedException("IC: deleteProduct() You cannot perform the following operation.");
    }

    // Export to Excel
    @GetMapping("/report")
    public void generateReport(HttpServletResponse response,
                               @RequestParam(defaultValue = "pmProduct.productID") String sortBy,
                               @RequestParam(defaultValue = "asc") String sortDirection) {
        logger.info("TotalItemsController: generateReport() calling...");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=TotalItemsReport.xlsx";
        response.setHeader(headerKey, headerValue);
        totalItemsService.generateReport(response, sortBy, sortDirection);
    }

}
