package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.SalesOrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/priority/inventory/outgoing-stock")
public class SoControllerInIc {

    private static final Logger logger = LoggerFactory.getLogger(SoControllerInIc.class);
    private final SalesOrderService salesOrderService;
    @Autowired
    public SoControllerInIc(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }


    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("OS Controller: Fetching orders - page: {}, size: {}, sortBy: {}, sortDir: {}",
                page, size, sortBy, sortDir);

        PaginatedResponse<SalesOrderResponseDto> orders = salesOrderService.getAllSalesOrdersSorted(page, size, sortBy, sortDir, Optional.empty());

        return ResponseEntity.ok(orders);
    }
}
