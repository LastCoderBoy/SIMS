package com.JK.SIMS.controller.inventoryControllers;

import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInInventory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_BY_FOR_SO;
import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_DIRECTION;
import static com.JK.SIMS.service.generalUtils.GlobalServiceHelper.getOptionDateValue;
import static com.JK.SIMS.service.generalUtils.GlobalServiceHelper.validateAndExtractToken;
import static com.JK.SIMS.service.generalUtils.SalesOrderServiceHelper.validateSalesOrderStatus;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/inventory/sales-order")
public class SoControllerInIc {

    private final SoServiceInInventory soServiceInInventory;

    @GetMapping
    public ResponseEntity<?> getAllWaitingSalesOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("IcSo: getAllOutgoingSalesOrders() fetching orders - page: {}, size: {}, sortBy: {}, sortDir: {}",
                page, size, sortBy, sortDir);
        PaginatedResponse<SummarySalesOrderView> orders =
                soServiceInInventory.getAllOutgoingSalesOrders(page, size, sortBy, sortDir);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/urgent")
    public ResponseEntity<?> getAllUrgentSalesOrders(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                                     @RequestParam(defaultValue = "orderReference") String sortBy,
                                                     @RequestParam(defaultValue = "desc") String sortDir){
        log.info("IcSo: getAllUrgentSalesOrders() calling...");
        PaginatedResponse<SalesOrderResponseDto> dtoResponse = soServiceInInventory.getAllUrgentSalesOrders(page, size, sortBy, sortDir);
        return ResponseEntity.ok(dtoResponse);
    }

    // Only High Roles can process the order
    // Stock Out button in the SO section
    @PutMapping("/stocks/out")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> bulkStockOutOrders(@Valid @RequestBody ProcessSalesOrderRequestDto request,
                                                @RequestHeader("Authorization") String token){
        log.info("IcSo: bulkStockOutOrders() called with {} orders", request.getItemQuantities().size());
        String jwtToken = validateAndExtractToken(token);
        ApiResponse<Void> response = soServiceInInventory.processSalesOrder(request, jwtToken);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> cancelSalesOrder(@PathVariable Long orderId, @RequestHeader("Authorization") String token){
        log.info("IcSo: cancelSalesOrder() calling...");
        String jwtToken = validateAndExtractToken(token);
        ApiResponse<Void> apiResponse = soServiceInInventory.cancelSalesOrder(orderId, jwtToken);
        return ResponseEntity.ok(apiResponse);
    }

    // Search by Order Reference, Customer name
    @GetMapping("/search")
    public ResponseEntity<?> searchInWaitingSalesOrders(@RequestParam(required = false) String text,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(defaultValue = "orderReference") String sortBy,
                                                        @RequestParam(defaultValue = "desc") String sortDir){
        log.info("IcSo: searchProduct() calling...");
        PaginatedResponse<SummarySalesOrderView> dtoResponse =
                soServiceInInventory.searchInWaitingSalesOrders(text, page, size, sortBy, sortDir);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterWaitingSalesOrders(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String optionDate,
                                                      @RequestParam(required = false) LocalDate startDate,
                                                      @RequestParam(required = false) LocalDate endDate,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(defaultValue = DEFAULT_SORT_BY_FOR_SO) String sortBy,
                                                      @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection){
        log.info("IcSo: filterProductsByStatus() calling...");
        // TODO: Use Proper Status not String
        try {
            SalesOrderStatus statusValue = validateSalesOrderStatus(status);
            String optionDateValue = getOptionDateValue(optionDate);
            PaginatedResponse<SummarySalesOrderView> dtoResponse =
                    soServiceInInventory.filterWaitingSoProducts(statusValue, optionDateValue, startDate, endDate, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(dtoResponse);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("IC-SO filterSalesOrders(): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}
