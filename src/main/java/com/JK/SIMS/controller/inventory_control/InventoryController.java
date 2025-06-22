package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.models.IC_models.InventoryDataDTO;
import com.JK.SIMS.models.IC_models.InventoryPageResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.IC_service.InventoryControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> loadInventoryControlData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: loadInventoryControlData() calling with page {} and size {}...", page, size);
        InventoryPageResponse inventoryPageResponse = icService.loadIcPageData(page, size);
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
        PaginatedResponse<InventoryDataDTO> inventoryDataDTOList = icService.searchProduct(text, page, size);
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
            @RequestParam(defaultValue = "product.name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<InventoryDataDTO> filteredDTOs = icService.filterProducts(filter, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filteredDTOs);
    }
}
