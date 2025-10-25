package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.config.security.SecurityUtils;
import com.JK.SIMS.config.security.TokenUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.UM_models.Roles;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.JK.SIMS.service.utilities.SalesOrderServiceHelper.validateSalesOrderStatus;

@RestController
@RequestMapping("/api/v1/products/manage-order/so/qr")
@Slf4j
public class SoQrCodeController {

    private final SoQrCodeService salesQrCodeService;
    @Autowired
    public SoQrCodeController(SoQrCodeService salesQrCodeService) {
        this.salesQrCodeService = salesQrCodeService;
    }

    @GetMapping("/{qrToken}")
    public ResponseEntity<?> verifyQrCode(@PathVariable String qrToken,
                                          @RequestHeader("Authorization") String token,
                                          HttpServletRequest request){
        if(token != null && !token.trim().isEmpty()) {
            log.info("SO-QR: verifyQrCode() is calling...");
            String jwtToken = TokenUtils.extractToken(token);
            DetailedSalesOrderView qrResponse = salesQrCodeService.verifyQrCode(qrToken, jwtToken, request);
            return ResponseEntity.ok(qrResponse);
        }
        log.error("SO-QR: verifyQrCode() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

    @PostMapping("/{qrToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COURIER')")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String qrToken, @RequestBody String status,
                                               HttpServletRequest request, @RequestHeader("Authorization") String token){
        if(token != null && !token.trim().isEmpty()) {
            log.info("SO-QR: updateOrderStatus() is calling...");
            SalesOrderStatus statusValue = validateSalesOrderStatus(status);
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse<String> response = salesQrCodeService.updateOrderStatus(qrToken, jwtToken, statusValue, request);
            return ResponseEntity.ok(response);
        }
        log.error("SO-QR: updateOrderStatus() Invalid Token provided. {}", token);
        throw new InvalidTokenException("Invalid Token provided. Please re-login.");
    }

}
