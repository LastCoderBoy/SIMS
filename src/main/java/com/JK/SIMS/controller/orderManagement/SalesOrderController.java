package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.config.security.TokenUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.BulkOrderItemsRequestDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static com.JK.SIMS.service.utilities.EntityConstants.*;
import static com.JK.SIMS.service.utilities.GlobalServiceHelper.getOptionDateValue;


@RestController
@Slf4j // will add a logger to the class
@RequestMapping("/api/v1/products/manage-order/so")
public class SalesOrderController {

    private final SoServiceInIc soServiceInIc;
    private final SalesOrderService salesOrderService;
    @Autowired
    public SalesOrderController(SoServiceInIc soServiceInIc, SalesOrderService salesOrderService) {
        this.soServiceInIc = soServiceInIc;
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    public ResponseEntity<?> getAllSummarySalesOrders(@RequestParam(defaultValue = DEFAULT_SORT_BY_FOR_SO) String sortBy,
                                                      @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
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
    public ResponseEntity<?> createOrder(@Valid @RequestBody SalesOrderRequestDto salesOrderRequestDto,
                                         @RequestHeader("Authorization") String token){
        log.info("OM-SO createOrder() is calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesOrderService.createOrder(salesOrderRequestDto, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        }
        log.error("OM-SO createOrder() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }


    // Only the Destination, CustomerName and Quantity can be updated.
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


    // Updating the SO by adding new items
    @PatchMapping("/{orderId}/items/add")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> addItemsToSalesOrder(@PathVariable Long orderId,
                                                  @Valid @RequestBody BulkOrderItemsRequestDto bulkOrderItemsRequestDto,
                                                  @RequestHeader("Authorization") String token){
        log.info("OM-SO: addItemsToSalesOrder() is calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesOrderService.addItemsToSalesOrder(orderId, bulkOrderItemsRequestDto, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        log.error("OM-SO: addItemsToSalesOrder() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<String>> removeItemFromSalesOrder(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestHeader("Authorization") String token) {
        log.info("OM-SO removeItemFromSalesOrder(): Called for orderId {}, itemId {}", orderId, itemId);
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("OM-SO: removeItemFromSalesOrder(): Invalid Token provided.");
        }
        ApiResponse<String> response = salesOrderService.removeItemFromSalesOrder(orderId, itemId, TokenUtils.extractToken(token));
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{orderId}/cancel-salesorder")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelSalesOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        log.info("OM-SO: cancelSalesOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<Void> response = soServiceInIc.cancelSalesOrder(orderId, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        log.error("OM-SO: cancelSalesOrder() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchInSalesOrder(@RequestParam(required = false) String text,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(defaultValue = DEFAULT_SORT_BY_FOR_SO) String sortBy,
                                                @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection){
        log.info("OM-SO: searchInSalesOrders() calling...");
        PaginatedResponse<SummarySalesOrderView> soResponse = salesOrderService.searchInSalesOrders(text, page, size, sortBy, sortDirection);
        return new ResponseEntity<>(soResponse, HttpStatus.OK);
    }


    @GetMapping("/filter")
    public ResponseEntity<?> filterSalesOrders(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String optionDate,
                                               @RequestParam(required = false) LocalDate startDate,
                                               @RequestParam(required = false) LocalDate endDate,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = DEFAULT_SORT_BY_FOR_SO) String sortBy,
                                               @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection){
        log.info("OM-SO: filterSalesOrders() calling...");

        try {
            SalesOrderStatus statusValue = null;
            if (status != null) {
                try {
                    statusValue = SalesOrderStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("OM-SO filterSalesOrders(): Invalid status value: {}", status);
                    throw new IllegalArgumentException("Invalid status value provided: " + status);
                }
            }
            String optionDateValue = getOptionDateValue(optionDate); // might throw IllegalArgumentException
            PaginatedResponse<SummarySalesOrderView> summaryResponse =
                    salesOrderService.filterSalesOrders(statusValue, optionDateValue, startDate, endDate, page, size, sortBy, sortDirection);
            return new ResponseEntity<>(summaryResponse, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("OM-SO filterSalesOrders(): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}









