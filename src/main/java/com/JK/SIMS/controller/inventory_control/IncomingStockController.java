package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.service.IC_service.IncomingStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/inventory/incoming-stock")
public class IncomingStockController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockController.class);
    private final IncomingStockService stockService;
    @Autowired
    public IncomingStockController(IncomingStockService stockService) {
        this.stockService = stockService;
    }

    // TODO: Add incoming stock functionality.

    // TODO: Process the result of the incoming stock to the PM
}
