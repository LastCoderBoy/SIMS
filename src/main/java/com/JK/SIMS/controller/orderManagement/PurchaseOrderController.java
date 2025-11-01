package com.JK.SIMS.controller.orderManagement;


import com.JK.SIMS.config.security.utils.TokenUtils;
import com.JK.SIMS.exception.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.purchaseOrder.dtos.PurchaseOrderRequestDto;
import com.JK.SIMS.models.purchaseOrder.dtos.views.DetailsPurchaseOrderView;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/manage-order/po")
public class PurchaseOrderController {

    private final Logger logger = LoggerFactory.getLogger(PurchaseOrderController.class);
    private final PurchaseOrderService purchaseOrderService;
    private final PoServiceInIc poServiceInIc;
    @Autowired
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService, PoServiceInIc poServiceInIc) {
        this.purchaseOrderService = purchaseOrderService;
        this.poServiceInIc = poServiceInIc;
    }

    @GetMapping
    public ResponseEntity<?> getAllSummaryPurchaseOrders(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(defaultValue = "asc") String sortDirection,
                                                         @RequestParam(defaultValue = "product.name") String sortBy){
        logger.info("OM-PO: getAllPurchaseOrders() calling...");
        PaginatedResponse<SummaryPurchaseOrderView> pageResponse =
                purchaseOrderService.getAllPurchaseOrders(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getDetailsForPurchaseOrderId(@PathVariable Long orderId){
        logger.info("OM-PO: getDetailsForPurchaseOrderId() calling for ID: {}", orderId);
        DetailsPurchaseOrderView detailsForPurchaseOrder = purchaseOrderService.getDetailsForPurchaseOrderId(orderId);
        return ResponseEntity.ok(detailsForPurchaseOrder);
    }

    @PostMapping("/create")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequestDto stockRequest,
                                                 @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        logger.info("OM-PO: createPurchaseOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<PurchaseOrderRequestDto> response =
                    purchaseOrderService.createPurchaseOrder(stockRequest, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        }
        throw new InvalidTokenException("OM-PO: createPurchaseOrder() Invalid Token provided.");
    }

    @PutMapping("{orderId}/cancel-purchaseorder")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelIncomingStockInternal(@PathVariable Long orderId,
                                                         @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<Void> response = poServiceInIc.cancelPurchaseOrderInternal(orderId, jwtToken);
            logger.info("OM-PO: cancelIncomingStockInternal() called for ID: {} is cancelled successfully.", orderId);
            return ResponseEntity.ok(response);
        }
        logger.error("OM-PO: cancelIncomingStockInternal() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPurchaseOrders(@RequestParam(required = false) String text,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(defaultValue = "product.name") String sortBy,
                                                  @RequestParam(defaultValue = "asc") String sortDirection){
        logger.info("OM-PO: searchPurchaseOrders() calling...");
        PaginatedResponse<SummaryPurchaseOrderView> response =
                purchaseOrderService.searchPurchaseOrders(text, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterPurchaseOrders(@RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "product.name") String sortBy,
                                                  @RequestParam(defaultValue = "asc") String sortDirection,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size){
        logger.info("OM-PO: FilterPurchaseOrders() calling...");
        PaginatedResponse<SummaryPurchaseOrderView> filterResponse =
                purchaseOrderService.filterPurchaseOrders(category, status, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filterResponse);
    }
}
