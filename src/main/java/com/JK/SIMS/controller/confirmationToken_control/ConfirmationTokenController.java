package com.JK.SIMS.controller.confirmationToken_control;

import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/SIMS") // this endpoint will not be authenticated.
public class ConfirmationTokenController {

    private final PurchaseOrderService purchaseOrderService;
    @Autowired
    public ConfirmationTokenController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping(value = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> confirmOrder(@RequestParam String token){
        String htmlResponse = purchaseOrderService.confirmPurchaseOrder(token);
        return ResponseEntity.ok(htmlResponse);
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> cancelOrder(@RequestParam String token){
        String htmlResponse = purchaseOrderService.cancelPurchaseOrder(token);
        return ResponseEntity.ok(htmlResponse);
    }

}
