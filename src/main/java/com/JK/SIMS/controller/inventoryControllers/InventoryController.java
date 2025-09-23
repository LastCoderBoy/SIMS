package com.JK.SIMS.controller.inventoryControllers;


import com.JK.SIMS.models.IC_models.salesOrder.BulkShipStockRequestDto;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import com.JK.SIMS.service.utilities.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.ReceiveStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.utilities.TokenUtils;
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
    public ResponseEntity<?> receiveIncomingStockOrder(@Valid @RequestBody ReceiveStockRequestDto receiveRequest,
                                                       @PathVariable Long orderId,
                                                       @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        logger.info("IC: receiveIncomingStockOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response =  poServiceInIc.receivePurchaseOrder(orderId, receiveRequest, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IC: receiveIncomingStockOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: receiveIncomingStockOrder() You cannot perform the following operation.");
    }


    // STOCK OUT button
    @PutMapping("/stocks/out")
    public ResponseEntity<?> bulkStockOutOrders(@Valid @RequestBody BulkShipStockRequestDto request,
                                                @RequestHeader("Authorization") String token){
        logger.info("IC: bulkStockOutOrders() called with {} orders", request.getBulkSoRequestDtos().size());
        if (!SecurityUtils.hasAccess()) {
            throw new org.springframework.security.access.AccessDeniedException("IC: bulkStockOutOrders() You cannot perform this operation.");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("IC: bulkStockOutOrders() Invalid Token provided.");
        }
        String jwtToken = TokenUtils.extractToken(token);
        ApiResponse response = soServiceInIc.processOrderRequest(request, jwtToken);
        return ResponseEntity.ok(response);
    }

    // CANCEL ORDER button
    @PutMapping("/{orderId}/cancel-order")
    public ResponseEntity<?> cancelOutgoingStockOrder(@PathVariable Long orderId,
                                                      @RequestHeader("Authorization") String token) throws AccessDeniedException {
        logger.info("IC: cancelOutgoingStockOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response = soServiceInIc.cancelSalesOrder(orderId, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IC: cancelOutgoingStockOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: cancelOutgoingStockOrder() You cannot perform the following operation.");
    }

    /**
     * Search for the PENDING Outgoing Stock Products based on the provided text.
     * @param text search text
     * @return ResponseEntity with search results
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchOutgoingInPendingProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IC: searchOutgoingStock() calling...");
        PaginatedResponse<SalesOrderResponseDto> outgoingStockDTOList =
                soServiceInIc.searchInOutgoingSalesOrders(text, page, size);
        return ResponseEntity.ok(outgoingStockDTOList);
    }

    /**
     * Sort products based on current stock, location, and status.
     * @param sortBy Field to sort by
     * @param sortDirection Sort direction (asc/desc)
     * @param page requested page number (zero-based)
     * @param size number of items per page
     * @return ResponseEntity with a filtered product list
     */
    @GetMapping("/filter")
    public ResponseEntity<?> sortOutgoingProductBy(
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: sortProductBy() calling with page {} and size {}...", page, size);
        PaginatedResponse<SalesOrderResponseDto> sortedDTOs =
                soServiceInIc.getAllWaitingSalesOrders(page, size, sortBy, sortDirection);
        return ResponseEntity.ok(sortedDTOs);
    }
}
