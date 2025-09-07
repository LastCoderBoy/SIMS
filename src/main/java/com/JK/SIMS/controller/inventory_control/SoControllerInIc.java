package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.salesOrderService.SalesOrderService;
import com.JK.SIMS.service.salesOrderService.SoServiceInIc;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products/inventory/sales-order")
public class SoControllerInIc {

    private static final Logger logger = LoggerFactory.getLogger(SoControllerInIc.class);
    private final SalesOrderService salesOrderService;
    private final SoServiceInIc soServiceInIc;
    @Autowired
    public SoControllerInIc(SalesOrderService salesOrderService, SoServiceInIc soServiceInIc) {
        this.salesOrderService = salesOrderService;
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

    //TODO: Urgent Shipment table (CurrentDate + 2 > estimatedDeliveryDate)

    //TODO: Average Fulfill Time.

    //TODO: Stock Out

    //TODO: Cancel Order
}
