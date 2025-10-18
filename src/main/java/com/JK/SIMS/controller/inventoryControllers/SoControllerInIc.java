package com.JK.SIMS.controller.inventoryControllers;

import com.JK.SIMS.config.security.TokenUtils;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("IcSo: getAllWaitingSalesOrders() fetching orders - page: {}, size: {}, sortBy: {}, sortDir: {}",
                page, size, sortBy, sortDir);
        PaginatedResponse<SummarySalesOrderView> orders =
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
    // Stock OUT button in the SO section
    @PutMapping("/stocks/out")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> bulkStockOutOrders(@Valid @RequestBody ProcessSalesOrderRequestDto request,
                                                @RequestHeader("Authorization") String token){
        logger.info("IcSo: bulkStockOutOrders() called with {} orders", request.getItemQuantities().size());
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("IcSo: bulkStockOutOrders() Invalid Token provided.");
        }
        String jwtToken = TokenUtils.extractToken(token);
        ApiResponse<Void> response = soServiceInIc.processOrderRequest(request, jwtToken);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelSalesOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        logger.info("IcSo: cancelSalesOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<Void> apiResponse = soServiceInIc.cancelSalesOrder(orderId, jwtToken);
            return ResponseEntity.ok(apiResponse);
        }
        throw new IllegalArgumentException("IcSo: cancelSalesOrder() Invalid Token provided.");
    }

    // Search by Order Reference, Customer name
    @GetMapping("/search")
    public ResponseEntity<?> searchInWaitingSalesOrders(@RequestParam(required = false) String text,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(defaultValue = "orderReference") String sortBy,
                                                        @RequestParam(defaultValue = "desc") String sortDir){
        logger.info("IcSo: searchProduct() calling...");
        PaginatedResponse<SummarySalesOrderView> dtoResponse =
                soServiceInIc.searchInWaitingSalesOrders(text, page, size, sortBy, sortDir);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterWaitingSalesOrders(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String optionDate,
                                                      @RequestParam(required = false) LocalDate startDate,
                                                      @RequestParam(required = false) LocalDate endDate,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size){
        logger.info("IcSo: filterProductsByStatus() calling...");

        SalesOrderStatus soStatus = null;
        if (status != null) {
            try {
                soStatus = SalesOrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid status value: {}", status);
            }
        }

        PaginatedResponse<SummarySalesOrderView> dtoResponse =
                soServiceInIc.filterSoProducts(soStatus, optionDate, startDate, endDate, page, size);
        return ResponseEntity.ok(dtoResponse);
    }
}
