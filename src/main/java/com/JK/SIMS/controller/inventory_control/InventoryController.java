package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataDto;
import com.JK.SIMS.models.IC_models.InventoryPageResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryControlService icService;
    @Autowired
    public InventoryController(InventoryControlService icService) {
        this.icService = icService;
    }

    /**
     * Load inventory control data with pagination.
     * @param page requested page number (zero-based)
     * @param size number of items per page
     * @return ResponseEntity with InventoryPageResponse
     */
    @GetMapping
    public ResponseEntity<?> getInventoryControlPageData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: getInventoryControlPageData() calling with page {} and size {}...", page, size);
        InventoryPageResponse inventoryPageResponse = icService.getInventoryControlPageData(page, size);
        return ResponseEntity.ok(inventoryPageResponse);
    }


    /**
     * Search for products based on the provided text.
     * @param text search text
     * @return ResponseEntity with search results
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IC: searchProduct() calling...");
        PaginatedResponse<InventoryDataDto> inventoryDataDTOList = icService.searchProduct(text, page, size);
        return ResponseEntity.ok(inventoryDataDTOList);
    }

    /**
     * Filter products based on current stock, location, and status.
     * @param filter Optional filter string in format "field:value". Supported fields: status, stock, location
     * @param sortBy Field to sort by
     * @param sortDirection Sort direction (asc/desc)
     * @param page requested page number (zero-based)
     * @param size number of items per page
     * @return ResponseEntity with a filtered product list
     */
    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "pmProduct.name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDto> filteredDTOs = icService.filterProducts(filter, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filteredDTOs);
    }

    @PutMapping("/{sku}")
    public ResponseEntity<?> updateProduct(@PathVariable String sku, @RequestBody InventoryData newInventoryData) throws BadRequestException {
        if(sku == null || sku.trim().isEmpty() || newInventoryData == null){
            throw new BadRequestException("IC: updateProduct() SKU or new input body cannot be null or empty");
        }
        ApiResponse response = icService.updateProduct(sku.toUpperCase(), newInventoryData);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<?> deleteProduct(@PathVariable String sku) throws BadRequestException {
        if(sku == null || sku.trim().isEmpty()){
            throw new BadRequestException("IC: deleteProduct() SKU cannot be empty");
        }
        logger.info("IC: deleteProduct() calling...");

        ApiResponse response = icService.deleteProduct(sku);
        return ResponseEntity.ok(response);
    }

}
