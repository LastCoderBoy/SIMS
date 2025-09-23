package com.JK.SIMS.controller.inventoryControllers;

import com.JK.SIMS.service.utilities.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.ReceiveStockRequestDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.utilities.TokenUtils;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/inventory/purchase-order")
public class PoControllerInIc {

    private static final Logger logger = LoggerFactory.getLogger(PoControllerInIc.class);
    private final PoServiceInIc poServiceInIc;
    @Autowired
    public PoControllerInIc(PoServiceInIc poServiceInIc) {
        this.poServiceInIc = poServiceInIc;
    }

    @GetMapping
    public ResponseEntity<?> getAllPendingPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IcPo: getAllPendingPurchaseOrders() calling with page {} and size {}", page, size);
        PaginatedResponse<PurchaseOrderResponseDto> paginatedStockResponse = poServiceInIc.getAllPendingPurchaseOrders(page, size);
        return ResponseEntity.ok(paginatedStockResponse);
    }

    @GetMapping("/overdue")
    public ResponseEntity<?> getAllOverduePurchaseOrders(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size){
        logger.info("IcPo: getPendingStockRecordsByStatus() calling with page {} and size {}", page, size);
        PaginatedResponse<PurchaseOrderResponseDto> response = poServiceInIc.getAllOverduePurchaseOrders(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPoProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IcPo: searchProduct() calling with text: {}", text);
        PaginatedResponse<PurchaseOrderResponseDto> dtoResponse = poServiceInIc.searchInIncomingPurchaseOrders(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterPoStock(@RequestParam(required = false) PurchaseOrderStatus status,
                                         @RequestParam(required = false) ProductCategories category,
                                         @RequestParam(defaultValue = "product.name") String sortBy,
                                         @RequestParam(defaultValue = "asc") String sortDirection,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<PurchaseOrderResponseDto> filterResponse =
                poServiceInIc.filterIncomingPurchaseOrders(status, category, sortBy, sortDirection, page, size);
        logger.info("IcPo filterStock(): Returning {} paginated data", filterResponse.getContent().size());
        return ResponseEntity.ok(filterResponse);
    }

    // Stock IN button in the PO section | High Roles can only accept the order
    @PutMapping("/{orderId}/receive")
    public ResponseEntity<?> receivePurchaseOrder(@Valid @RequestBody ReceiveStockRequestDto receiveRequest,
                                                       @PathVariable Long orderId,
                                                       @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        logger.info("IcPo receivePurchaseOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response =  poServiceInIc.receivePurchaseOrder(orderId, receiveRequest, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IcPo: receivePurchaseOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IcPo: receivePurchaseOrder() You cannot perform the following operation.");
    }

    // Cancel button in the PO section.
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelPurchaseOrderInternal(@PathVariable Long orderId,
                                                         @RequestHeader("Authorization") String token) throws AccessDeniedException, BadRequestException {
        logger.info("IcPo cancelPurchaseOrderInternal() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response = poServiceInIc.cancelPurchaseOrderInternal(orderId, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IcPo: cancelPurchaseOrderInternal() Invalid Token provided.");
        }
        throw new AccessDeniedException("IcPo: cancelPurchaseOrderInternal() You cannot perform the following operation.");
    }

    //TODO:
    // Display Mini Chart, Stock Impact Review
    // View in OM and PM sections
}
