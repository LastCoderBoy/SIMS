package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j // will add a logger to the class
@RequestMapping("/api/v1/products/manage-order/so")
public class SalesOrderController {

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody SalesOrderRequestDto salesOrderRequestDto){
        log.info("OS createOrder() is calling...");
        ApiResponse response = salesOrderService.createOrder(salesOrderRequestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
