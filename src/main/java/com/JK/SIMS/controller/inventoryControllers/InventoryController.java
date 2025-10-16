package com.JK.SIMS.controller.inventoryControllers;


import com.JK.SIMS.config.security.TokenUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.ReceiveStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/products/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryControlService icService;
    private final PoServiceInIc poServiceInIc;
    private final SoServiceInIc soServiceInIc;
    @Autowired
    public InventoryController(InventoryControlService icService, PoServiceInIc poServiceInIc, SoServiceInIc soServiceInIc) {
        this.icService = icService;
        this.poServiceInIc = poServiceInIc;
        this.soServiceInIc = soServiceInIc;
    }


    @GetMapping
    public ResponseEntity<?> getInventoryControlPageData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: getInventoryControlPageData() calling with page {} and size {}...", page, size);
        InventoryPageResponse inventoryPageResponse = icService.getInventoryControlPageData(page, size);
        return ResponseEntity.ok(inventoryPageResponse);
    }


    // STOCK IN button
    @PutMapping("/{orderId}/receive")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> receiveIncomingStockOrder(@Valid @RequestBody ReceiveStockRequestDto receiveRequest,
                                                       @PathVariable Long orderId,
                                                       @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        logger.info("IC: receiveIncomingStockOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<Void> response =  poServiceInIc.receivePurchaseOrder(orderId, receiveRequest, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        throw new InvalidTokenException("IC: receiveIncomingStockOrder() Invalid Token provided.");
    }


    // STOCK OUT button
    @PutMapping("/stocks/out")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> bulkStockOutOrders(@Valid @RequestBody ProcessSalesOrderRequestDto request,
                                                @RequestHeader("Authorization") String token){
        logger.info("IC: bulkStockOutOrders() called with {} orders", request.getItemQuantities().size());
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("IC: bulkStockOutOrders() Invalid Token provided.");
        }
        String jwtToken = TokenUtils.extractToken(token);
        ApiResponse<Void> response = soServiceInIc.processOrderRequest(request, jwtToken);
        return ResponseEntity.ok(response);
    }

    // CANCEL ORDER button
    @PutMapping("/{orderId}/cancel-order")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelOutgoingStockOrder(@PathVariable Long orderId,
                                                      @RequestHeader("Authorization") String token) throws AccessDeniedException {
        logger.info("IC: cancelOutgoingStockOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<Void> response = soServiceInIc.cancelSalesOrder(orderId, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        throw new InvalidTokenException("IC: cancelOutgoingStockOrder() Invalid Token provided.");
    }

    /**
     * Search for the PENDING Sales and Purchase Orders based on the provided text.
     * @param text search text
     * @return ResponseEntity with search results
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchOutgoingInPendingProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IC: searchOutgoingInPendingProduct() calling...");
        PaginatedResponse<PendingOrdersResponseDto> pendingOrdersDtos =
                icService.searchByTextPendingOrders(text, page, size);
        return ResponseEntity.ok(pendingOrdersDtos);
    }

    // Filter by Type, Status, Category, Date, Start Date, End Date
    @GetMapping("/filter")
    public ResponseEntity<?> filterPendingOrders(@RequestParam(required = false) String type, // "SALES_ORDER" or "PURCHASE_ORDER"
                                                 @RequestParam(required = false) String status, // SalesOrderStatus or PurchaseOrderStatus
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String dateOption, // "orderDate" or "estimatedDate"
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                 @RequestParam(defaultValue = "id") String sortBy,
                                                 @RequestParam(defaultValue = "asc") String sortDirection,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        // Parse status (handle both SO and PO statuses)
        SalesOrderStatus soStatus = null;
        PurchaseOrderStatus poStatus = null;
        if (status != null) {
            try {
                soStatus = SalesOrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                try {
                    poStatus = PurchaseOrderStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    logger.warn("Invalid status value: {}", status);
                }
            }
        }

        // Parse category (if provided)
        ProductCategories productCategory = null;
        if (category != null) {
            try {
                productCategory = ProductCategories.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid category value: {}", category);
            }
        }

        // Delegate to service
        PaginatedResponse<PendingOrdersResponseDto> result =
                icService.filterPendingOrders(type, soStatus, poStatus, dateOption,
                        startDate, endDate, productCategory, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(result);
    }

}
