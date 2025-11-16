package com.JK.SIMS.controller.inventoryControllers;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlRequest;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.service.InventoryServices.totalItemsService.TotalItemsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_BY;
import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_DIRECTION;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/products/inventory/total")
public class TotalItemsController {
    private final TotalItemsService totalItemsService;


    @GetMapping
    public ResponseEntity<?> getAllProducts(@RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
                                            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size){
        log.info("TotalItemsController: getAllProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> inventoryResponse =
                totalItemsService.getAllPaginatedInventoryResponse(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(inventoryResponse);
    }

    // Used to update the IC Stock levels.
    @PutMapping("/{sku}/update")
    public ResponseEntity<?> updateProduct(@PathVariable String sku,
                                           @RequestBody InventoryControlRequest inventoryControlRequest) throws BadRequestException {
        if(sku == null || sku.trim().isEmpty() || inventoryControlRequest == null){
            throw new BadRequestException("IC: updateProduct() SKU or new input body cannot be null or empty");
        }
        ApiResponse<Void> response = totalItemsService.updateProduct(sku.toUpperCase(), inventoryControlRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(@RequestParam(required = false) String text,
                                           @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
                                           @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size){
        log.info("TotalItemsController: searchProduct() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> inventoryResponse =
                totalItemsService.searchProduct(text, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(inventoryResponse);
    }

    // Can filter by Stock Status, Product Category, and <= Stock Level
    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam String filterBy,
            @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("TotalItemsController: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryControlResponse> filterResponse =
                totalItemsService.filterProducts(filterBy, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filterResponse);
    }

    @DeleteMapping("/{sku}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> deleteProduct(@PathVariable String sku) throws BadRequestException{
        if(sku == null || sku.trim().isEmpty()){
            throw new BadRequestException("SKU cannot be empty");
        }
        log.info("IC: deleteProduct() calling...");
        ApiResponse<Void> response = totalItemsService.deleteProduct(sku);
        return ResponseEntity.ok(response);
    }

    // Export to Excel
    @GetMapping("/report")
    public void generateReport(HttpServletResponse response,
                               @RequestParam(defaultValue = "pmProduct.productID") String sortBy,
                               @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection) {
        log.info("TotalItemsController: generateTotalItemsReport() calling...");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=TotalItemsReport.xlsx";
        response.setHeader(headerKey, headerValue);
        totalItemsService.generateTotalItemsReport(response, sortBy, sortDirection);
    }

}
