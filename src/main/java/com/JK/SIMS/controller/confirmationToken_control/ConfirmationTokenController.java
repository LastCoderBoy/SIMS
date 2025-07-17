package com.JK.SIMS.controller.confirmationToken_control;

import com.JK.SIMS.service.incomingStock_service.IncomingStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/SIMS") // this endpoint will not be authenticated.
public class ConfirmationTokenController {

    private final IncomingStockService incomingStockService;
    @Autowired
    public ConfirmationTokenController(IncomingStockService incomingStockService) {
        this.incomingStockService = incomingStockService;
    }

    @GetMapping(value = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> confirmOrder(@RequestParam String token){
        String htmlResponse = incomingStockService.confirmPurchaseOrder(token);
        return ResponseEntity.ok(htmlResponse);
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> cancelOrder(@RequestParam String token){
        String htmlResponse = incomingStockService.cancelPurchaseOrder(token);
        return ResponseEntity.ok(htmlResponse);
    }

}
