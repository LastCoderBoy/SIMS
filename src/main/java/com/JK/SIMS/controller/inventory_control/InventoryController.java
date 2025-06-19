package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.models.IC_models.InventoryDataResponse;
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
     * @return ResponseEntity with InventoryDataResponse
     */
    @GetMapping
    public ResponseEntity<?> loadInventoryControlData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: loadInventoryControlData() calling with page {} and size {}...", page, size);
        InventoryDataResponse inventoryDataResponse = icService.loadIcPageData(page, size);
        return ResponseEntity.ok(inventoryDataResponse);
    }


    /**
     * Search for products based on the provided text.
     * @param text search text
     * @return ResponseEntity with search results
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(@RequestParam(required = false) String text){
        logger.info("IC: searchProduct() calling...");
        return icService.searchProduct(text);
    }

    /**
     * Filter products based on current stock, location, and status.
     * @param currentStock boolean flag to filter by current stock
     * @param sortByLocation boolean flag to sort by location
     * @param status optional status filter
     * @param page requested page number (zero-based)
     * @param size number of items per page
     * @return ResponseEntity with filtered product list
     */
    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam(required = false) boolean currentStock,
            @RequestParam(required = false) boolean sortByLocation,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: filterProducts() calling with page {} and size {}...", page, size);
        return icService.filterProducts(currentStock, sortByLocation, status, page, size);
    }
}
