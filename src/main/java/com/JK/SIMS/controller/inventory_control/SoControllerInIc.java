package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.salesOrderService.SoServiceInIc;
import com.JK.SIMS.service.utilities.SecurityUtils;
import com.JK.SIMS.service.utilities.TokenUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/products/inventory/sales-order")
public class SoControllerInIc {

    private static final Logger logger = LoggerFactory.getLogger(SoControllerInIc.class);
    private final SoServiceInIc soServiceInIc;
    @Autowired
    public SoControllerInIc(SoServiceInIc soServiceInIc) {
        this.soServiceInIc = soServiceInIc;
    }


    @GetMapping
    public ResponseEntity<?> getAllWaitingSalesOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "orderReference") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("IcSo: getAllWaitingSalesOrders() fetching orders - page: {}, size: {}, sortBy: {}, sortDir: {}",
                page, size, sortBy, sortDir);
        PaginatedResponse<SalesOrderResponseDto> orders =
                soServiceInIc.getAllWaitingSalesOrders(page, size, sortBy, sortDir);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/urgent")
    public ResponseEntity<?> getAllUrgentSalesOrders(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                                     @RequestParam(defaultValue = "orderReference") String sortBy,
                                                     @RequestParam(defaultValue = "desc") String sortDir){
        logger.info("IcSo: getAllUrgentSalesOrders() calling...");
        PaginatedResponse<SalesOrderResponseDto> dtoResponse = soServiceInIc.getAllUrgentSalesOrders(page, size, sortBy, sortDir);
        return ResponseEntity.ok(dtoResponse);
    }

    // Only High Roles can process the order
    @PutMapping("/{orderId}/out")
    public ResponseEntity<?> stockOutOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        logger.info("IcSo: stockOutOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse apiResponse = soServiceInIc.processOrderRequest(orderId, jwtToken);
                return ResponseEntity.ok(apiResponse);
            }
            throw new IllegalArgumentException("IcSo: stockOutOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IcSo: stockOutOrder() You cannot perform the following operation.");
    }

    //TODO: Consider Bulk update for each Cancel and Stock-out options.

    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelSalesOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        logger.info("IcSo: cancelSalesOrder() calling...");
        if(SecurityUtils.hasAccess()) {
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                ApiResponse apiResponse = soServiceInIc.cancelSalesOrder(orderId, jwtToken);
                return ResponseEntity.ok(apiResponse);
            }
            throw new IllegalArgumentException("IcSo: cancelSalesOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IcSo: cancelSalesOrder() You cannot perform the following operation.");
    }

    // Search by Product Name, Category, Order Reference, Destination
    @GetMapping("/search")
    public ResponseEntity<?> searchSoProduct(@RequestParam(required = false) String text,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size){
        logger.info("IcSo: searchProduct() calling...");
        PaginatedResponse<SalesOrderResponseDto> dtoResponse = soServiceInIc.searchInOutgoingSalesOrders(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterSoProductsByStatus( @RequestParam(required = false) SalesOrderStatus status,
                                                       @RequestParam(required = false) String optionDate,
                                                       @RequestParam(required = false) LocalDate startDate,
                                                       @RequestParam(required = false) LocalDate endDate,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size){
        logger.info("IcSo: filterProductsByStatus() calling...");
        PaginatedResponse<SalesOrderResponseDto> dtoResponse =
                soServiceInIc.filterSoProducts(status, optionDate, startDate, endDate, page, size);
        return ResponseEntity.ok(dtoResponse);
    }
}
