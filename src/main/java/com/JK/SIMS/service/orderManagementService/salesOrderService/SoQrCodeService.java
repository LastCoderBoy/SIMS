package com.JK.SIMS.service.orderManagementService.salesOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import com.JK.SIMS.models.salesOrder.qrcode.dtos.QrCodeUrlResponse;
import com.google.zxing.WriterException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.Duration;

public interface SoQrCodeService {
    DetailedSalesOrderView verifyQrCode(String qrToken, String jwtToken, HttpServletRequest request);
    ApiResponse<String> updateOrderStatus(String qrToken,  String jwtToken, SalesOrderStatus statusValue, HttpServletRequest request);
    SalesOrderQRCode generateAndLinkQrCode(String orderReference) throws IOException, WriterException;
    void deleteQrCodeFromS3(String s3Key);
    QrCodeUrlResponse getPresignedQrCodeUrl(Long salesOrderId);
}
