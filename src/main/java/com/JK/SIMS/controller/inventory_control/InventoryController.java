package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryPageResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.ReceiveStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.InventoryControl_service.SalesOrderService;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryControlService icService;
    private final PurchaseOrderService purchaseOrderService;
    private final SalesOrderService salesOrderService;
    @Autowired
    public InventoryController(InventoryControlService icService, PurchaseOrderService purchaseOrderService, SalesOrderService salesOrderService) {
        this.icService = icService;
        this.purchaseOrderService = purchaseOrderService;
        this.salesOrderService = salesOrderService;
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
                ApiResponse response =  purchaseOrderService.receiveIncomingStock(orderId, receiveRequest, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IC: receiveIncomingStockOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: receiveIncomingStockOrder() You cannot perform the following operation.");
    }


    // STOCK OUT button
    @PutMapping("/{orderId}/process-order")
    public ResponseEntity<?> processOrderRequest(@PathVariable Long orderId,
                                                   @RequestHeader("Authorization") String token) throws AccessDeniedException {
        logger.info("IC: processOrderedProduct() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response = salesOrderService.processOrderRequest(orderId, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IC: processOrderedProduct() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: processOrderedProduct() You cannot perform the following operation.");
    }

    // CANCEL ORDER button
    @PutMapping("/{orderId}/cancel-order")
    public ResponseEntity<?> cancelOutgoingStockOrder(@PathVariable Long orderId,
                                                      @RequestHeader("Authorization") String token) throws AccessDeniedException {
        logger.info("IC: cancelOutgoingStockOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse response = salesOrderService.cancelOutgoingStockOrder(orderId, jwtToken);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            throw new InvalidTokenException("IC: cancelOutgoingStockOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: cancelOutgoingStockOrder() You cannot perform the following operation.");
    }

    // Used to update the IC levels.
    @PutMapping("/{sku}")
    public ResponseEntity<?> updateProduct(@PathVariable String sku, @RequestBody InventoryData newInventoryData) throws BadRequestException {
        if(sku == null || sku.trim().isEmpty() || newInventoryData == null){
            throw new BadRequestException("IC: updateProduct() SKU or new input body cannot be null or empty");
        }
        ApiResponse response = icService.updateProduct(sku.toUpperCase(), newInventoryData);
        return ResponseEntity.ok(response);
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
                salesOrderService.searchOutgoingStock(text, page, size, Optional.of(SalesOrderStatus.PENDING));
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
                salesOrderService.getAllSalesOrdersSorted(page, size, sortBy, sortDirection, Optional.of(SalesOrderStatus.PENDING));
        return ResponseEntity.ok(sortedDTOs);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<?> deleteProduct(@PathVariable String sku) throws BadRequestException, AccessDeniedException {
        if(SecurityUtils.hasAccess()) {
            if(sku == null || sku.trim().isEmpty()){
                throw new BadRequestException("IC: deleteProduct() SKU cannot be empty");
            }
            logger.info("IC: deleteProduct() calling...");

            ApiResponse response = icService.deleteProduct(sku);
            return ResponseEntity.ok(response);
        }
        throw new AccessDeniedException("IC: deleteProduct() You cannot perform the following operation.");
    }

}
