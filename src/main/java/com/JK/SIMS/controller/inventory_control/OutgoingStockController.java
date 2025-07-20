package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.service.InventoryControl_service.OutgoingStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/priority/inventory/outgoing-stock")
public class OutgoingStockController {

    private static final Logger logger = LoggerFactory.getLogger(OutgoingStockController.class);
    private final OutgoingStockService outgoingStockService;
    @Autowired
    public OutgoingStockController(OutgoingStockService outgoingStockService) {
        this.outgoingStockService = outgoingStockService;
    }
}
