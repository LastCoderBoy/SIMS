package com.JK.SIMS.controller.inventoryControllers;


import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.service.InventoryServices.lowStockService.LowStockService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/products/inventory/low-stock")
public class LowStockController {
    private final LowStockService lowStockService;

    // Get All
    @GetMapping
    public ResponseEntity<PaginatedResponse<InventoryControlResponse>> getAllLowStockRecords(@RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                                   @RequestParam(defaultValue = "desc") String sortDirection,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size){
        log.info("LowStockController: getAllPaginatedLowStockRecords() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> response =
                lowStockService.getAllPaginatedLowStockRecords(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<InventoryControlResponse>> searchProduct(@RequestParam(required = false) String text,
                                           @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                           @RequestParam(defaultValue = "desc") String sortDirection,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size){
        log.info("LowStockController: searchProduct() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> inventoryResponse =
                lowStockService.searchInLowStockProducts(text, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(inventoryResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<PaginatedResponse<InventoryControlResponse>> filterProducts(@RequestParam(required = false) ProductCategories category,
                                            @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                            @RequestParam(defaultValue = "desc") String sortDirection,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size){
        log.info("LowStockController: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> filterResponse =
                lowStockService.filterLowStockProducts(category, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filterResponse);
    }

    @GetMapping("/report")
    public void generateLowStockReport(HttpServletResponse response,
                                       @RequestParam(defaultValue = "pmProduct.name") String sortBy,
                                       @RequestParam(defaultValue = "desc") String sortDirection){
        log.info("LowStockController: generateLowStockReport() calling...");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=LowStockReport.xlsx";
        response.setHeader(headerKey, headerValue);
        lowStockService.generateLowStockReport(response, sortBy, sortDirection);
    }
}
