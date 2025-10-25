package com.JK.SIMS.service.orderManagementService.salesOrderService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.DetailedSalesOrderView;
import jakarta.servlet.http.HttpServletRequest;

public interface SoQrCodeService {
    DetailedSalesOrderView verifyQrCode(String qrToken, String jwtToken, HttpServletRequest request);
    ApiResponse<String> updateOrderStatus(String qrToken,  String jwtToken, SalesOrderStatus statusValue, HttpServletRequest request);
}
