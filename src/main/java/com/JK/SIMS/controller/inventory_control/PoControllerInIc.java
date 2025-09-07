package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.purchaseOrderService.PoService;
import com.JK.SIMS.service.purchaseOrderService.PurchaseOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/inventory/purchase-order")
public class PoControllerInIc {

    private static final Logger logger = LoggerFactory.getLogger(PoControllerInIc.class);
    private final PoService poService;
    @Autowired
    public PoControllerInIc(PoService poService) {
        this.poService = poService;
    }

    @GetMapping
    public ResponseEntity<?> getAllIncomingStockRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IcPo: getAllIncomingStockRecords() calling with page {} and size {}", page, size);
        PaginatedResponse<PurchaseOrderResponseDto> paginatedStockResponse = poService.getAllIncomingStockRecords(page, size);
        return ResponseEntity.ok(paginatedStockResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IcPo: searchProduct() calling with text: {}", text);
        PaginatedResponse<PurchaseOrderResponseDto> dtoResponse = poService.searchInPendingProduct(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterStock(@RequestParam(required = false) PurchaseOrderStatus status,
                                         @RequestParam(required = false) ProductCategories category,
                                         @RequestParam(defaultValue = "product.name") String sortBy,
                                         @RequestParam(defaultValue = "asc") String sortDirection,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<PurchaseOrderResponseDto> filterResponse =
                poService.filterIncomingStock(status, category, sortBy, sortDirection, page, size);
        logger.info("IS filterStock(): Returning {} paginated data", filterResponse.getContent().size());
        return ResponseEntity.ok(filterResponse);
    }

}
