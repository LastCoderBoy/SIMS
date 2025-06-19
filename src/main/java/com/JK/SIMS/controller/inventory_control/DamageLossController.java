package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.service.IC_service.DamageLossService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/inventory/damage-loss")
public class DamageLossController {

    private final DamageLossService damageLossService;

    public DamageLossController(DamageLossService damageLossService) {
        this.damageLossService = damageLossService;
    }
}
