package com.JK.SIMS.controller.confirmationToken_control;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.ConfirmPoRequestDto;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.impl.PurchaseOrderServiceImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/SIMS") // this endpoint will not be authenticated.
public class ConfirmationController {

    private final PurchaseOrderServiceImpl purchaseOrderServiceImpl;
    private final ConfirmationTokenService confirmationTokenService;
    @Autowired
    public ConfirmationController(PurchaseOrderServiceImpl purchaseOrderServiceImpl, ConfirmationTokenService confirmationTokenService) {
        this.purchaseOrderServiceImpl = purchaseOrderServiceImpl;
        this.confirmationTokenService = confirmationTokenService;
    }

    @GetMapping("/confirm-form")
    public ResponseEntity<?> getConfirmationFormData(@RequestParam String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.validateConfirmationToken(token);
        if (confirmationToken == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Invalid or expired token."));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("poNumber", confirmationToken.getOrder().getPONumber());
        return ResponseEntity.ok(data);
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> processConfirmation(@RequestParam String token,
                                                 @Valid @RequestBody ConfirmPoRequestDto dto) {
        ApiResponse<String> response = purchaseOrderServiceImpl.confirmPurchaseOrder(token, dto.getExpectedArrivalDate());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/confirm-status")
    public ResponseEntity<?> getConfirmationStatus(@RequestParam String token) {
        Map<String, String> data = confirmationTokenService.getConfirmationStatus(token);
        return ResponseEntity.ok(data);
    }


    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> cancelOrder(@RequestParam String token){
        purchaseOrderServiceImpl.cancelPurchaseOrder(token);
        Map<String, String> data = new HashMap<>();
        data.put("message", "Order cancelled successfully.");
        data.put("alertClass", "alert-success");
        return ResponseEntity.ok(data);
    }

}
