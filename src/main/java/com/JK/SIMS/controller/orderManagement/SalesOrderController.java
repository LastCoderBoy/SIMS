package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.config.security.TokenUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j // will add a logger to the class
@RequestMapping("/api/v1/products/manage-order/so")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    @Autowired
    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<SummarySalesOrderView>> getAllSummarySalesOrders(@RequestParam(defaultValue = "orderReference") String sortBy,
                                                                   @RequestParam(defaultValue = "asc") String sortDirection,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "10") int size){
        log.info("OM-SO: getAllSummarySalesOrders() is calling...");
        PaginatedResponse<SummarySalesOrderView> summaryView =
                salesOrderService.getAllSummarySalesOrders(sortBy, sortDirection, page, size);
        return new ResponseEntity<>(summaryView, HttpStatus.OK);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getDetailsForSalesOrderId(@PathVariable Long orderId){
        log.info("OM-SO: getDetailsForPurchaseOrderId() is calling for ID: {}", orderId);
        DetailedSalesOrderView detailedView = salesOrderService.getDetailsForSalesOrderId(orderId);
        return new ResponseEntity<>(detailedView, HttpStatus.OK);
    }

    @PostMapping("/create")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> createOrder(@RequestBody SalesOrderRequestDto salesOrderRequestDto,
                                         @RequestHeader("Authorization") String token){
        log.info("OM-SO createOrder() is calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesOrderService.createOrder(salesOrderRequestDto, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        }
        log.error("OM-SO createOrder() Invalid Token provided. {}" , token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @PutMapping("/{orderId}/update")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> updateSalesOrder(@PathVariable Long orderId,
                                              @RequestBody SalesOrderRequestDto salesOrderRequestDto,
                                              @RequestHeader("Authorization") String token){
        log.info("OM-SO updateSalesOrder() is calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesOrderService.updateSalesOrder(orderId, salesOrderRequestDto, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        log.error("OM-SO updateSalesOrder() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @PutMapping("/{orderId}/cancel-salesorder")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelSalesOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        log.info("OM-SO: cancelSalesOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesOrderService.cancelSalesOrder(orderId, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        log.error("OM-SO: cancelSalesOrder() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }


    // TODO: Search and Filter logics as well


}









