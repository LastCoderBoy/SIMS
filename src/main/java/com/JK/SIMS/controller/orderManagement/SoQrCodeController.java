package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderStatusRequest;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.qrcode.dtos.QrCodeUrlResponse;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SoQrCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.JK.SIMS.service.generalUtils.GlobalServiceHelper.validateAndExtractToken;

@RestController
@RequestMapping("/api/v1/products/manage-order/so/qrcode")
@Slf4j
public class SoQrCodeController {

    private final SoQrCodeService salesQrCodeService;
    @Autowired
    public SoQrCodeController(SoQrCodeService salesQrCodeService) {
        this.salesQrCodeService = salesQrCodeService;
    }

    @GetMapping("/{salesOrderId}/view")
    public ResponseEntity<ApiResponse<QrCodeUrlResponse>> viewQrCode(@PathVariable Long salesOrderId){
        log.info("SO-QR: viewQrCode() is calling...");
        QrCodeUrlResponse qrCodeUrlResponse = salesQrCodeService.getPresignedQrCodeUrl(salesOrderId);
        return ResponseEntity.ok(new ApiResponse<>(true, "QR Code URL generated successfully", qrCodeUrlResponse));
    }

    // Maybe we can add RateLimiter later.
    @GetMapping("/{qrToken}/verify")
    public ResponseEntity<DetailedSalesOrderView> verifyQrCode(@PathVariable @NotBlank(message = "QR token is required") String qrToken,
                                          @RequestHeader("Authorization") String token,
                                          HttpServletRequest request){
        log.info("SO-QR: verifyQrCode() is calling...");
        String jwtToken = validateAndExtractToken(token);
        DetailedSalesOrderView qrResponse = salesQrCodeService.verifyQrCode(qrToken, jwtToken, request);
        return ResponseEntity.ok(qrResponse);
    }

    @PatchMapping("/{qrToken}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COURIER')")
    public ResponseEntity<ApiResponse<String>> updateOrderStatus(@PathVariable String qrToken,
                                               @Valid @RequestBody SalesOrderStatusRequest statusRequest,
                                               HttpServletRequest request,
                                               @RequestHeader("Authorization") String token){
        log.info("SO-QR: updateOrderStatus() is calling...");
        String jwtToken = validateAndExtractToken(token);
        ApiResponse<String> response = salesQrCodeService.updateOrderStatus(qrToken, jwtToken, statusRequest.getStatus(), request);
        return ResponseEntity.ok(response);
    }

    // TODO: The Frontend will implement the Download and Print logics.
}
