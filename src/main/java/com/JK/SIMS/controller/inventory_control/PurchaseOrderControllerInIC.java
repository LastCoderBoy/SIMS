package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderRequestDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.utilities.TokenUtils;
import com.JK.SIMS.service.purchaseOrderService.PurchaseOrderService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/priority/inventory/purchase-order")
public class PurchaseOrderControllerInIC {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderControllerInIC.class);
    private final PurchaseOrderService purchaseOrderService;
    @Autowired
    public PurchaseOrderControllerInIC(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public ResponseEntity<?> getAllIncomingStockRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        PaginatedResponse<PurchaseOrderResponseDto> paginatedStockResponse =
                purchaseOrderService.getAllIncomingStockRecords(page, size);
        return ResponseEntity.ok(paginatedStockResponse);
    }

    @PostMapping
    public ResponseEntity<?> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequestDto stockRequest,
                                                 @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        logger.info("IS: createPurchaseOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                purchaseOrderService.createPurchaseOrder(stockRequest, jwtToken);

                return new ResponseEntity<>(
                        new ApiResponse(true, stockRequest.getProductId() + " is ordered successfully"),
                        HttpStatus.CREATED);
            }
            throw new InvalidTokenException("IS: createPurchaseOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IS createPurchaseOrder(): You cannot perform the following operation.");
    }

    @PutMapping("{id}/cancel-order")
    public ResponseEntity<?> cancelIncomingStockInternal(@PathVariable Long id, @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response = purchaseOrderService.cancelIncomingStockInternal(id, jwtToken);
                return ResponseEntity.ok(response);
            }
            throw new InvalidTokenException("IS: cancelIncomingStockInternal() Invalid Token provided.");
        }
        throw new AccessDeniedException("IS: cancelIncomingStockInternal() You cannot perform the following operation.");
    }


    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IS: searchProduct() calling with text: {}", text);
        PaginatedResponse<PurchaseOrderResponseDto> dtoResponse = purchaseOrderService.searchProduct(text, page, size);
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
                purchaseOrderService.filterIncomingStock(status, category, sortBy, sortDirection, page, size);
        logger.info("IS filterStock(): Returning {} paginated data", filterResponse.getContent().size());
        return ResponseEntity.ok(filterResponse);
    }

}
