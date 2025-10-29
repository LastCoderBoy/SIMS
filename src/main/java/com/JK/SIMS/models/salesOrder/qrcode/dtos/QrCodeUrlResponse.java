package com.JK.SIMS.models.salesOrder.qrcode.dtos;

import java.time.LocalDateTime;

public record QrCodeUrlResponse(
        String qrImageUrl,
        String orderReference,
        LocalDateTime expiresAt
) {}
