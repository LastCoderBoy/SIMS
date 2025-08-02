package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.models.IC_models.InventoryDataDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.InventoryControl_service.totalItemsService.TotalItemsService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        PaginatedResponse<InventoryDataDto> inventoryResponse = totalItemsService.getPaginatedInventoryDto(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(inventoryResponse);
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

    // Export to Excel
    @GetMapping("/report")
    public void generateReport(HttpServletResponse response,
                               @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                               @RequestParam(defaultValue = "asc") String sortDirection) {
        logger.info("TotalItemsController: generateReport() calling...");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=TotalItemsReport.xlsx";
        response.setHeader(headerKey, headerValue);
        totalItemsService.generateReport(response, sortBy, sortDirection);
    }

}
