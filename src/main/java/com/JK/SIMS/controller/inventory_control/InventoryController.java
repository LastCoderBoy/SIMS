package com.JK.SIMS.controller.inventory_control;


import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataResponse;
import com.JK.SIMS.service.IC_service.InventoryControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/inventory")
public class InventoryController {

    private final InventoryControlService service;
    @Autowired
    public InventoryController(InventoryControlService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<InventoryDataResponse>> getAllInventoryProducts(){
        return service.getAllInventoryProducts();
    }

}
