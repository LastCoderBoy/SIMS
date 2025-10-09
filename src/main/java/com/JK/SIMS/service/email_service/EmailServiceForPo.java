package com.JK.SIMS.service.email_service;

import com.JK.SIMS.models.ApiResponse;

import java.time.LocalDate;

public interface EmailServiceForPo {
    ApiResponse<String> confirmPurchaseOrder(String token, LocalDate expectedArrivalDate);
    String cancelPurchaseOrder(String token);
}
