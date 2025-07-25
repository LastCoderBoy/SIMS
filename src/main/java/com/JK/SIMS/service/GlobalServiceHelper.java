package com.JK.SIMS.service;

import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.service.userManagement_service.JWTService;
import lombok.AllArgsConstructor;
import org.apache.coyote.BadRequestException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class GlobalServiceHelper {

    public static JWTService jwtService;

    public static boolean amongInvalidStatus(ProductStatus status) {
        return status.equals(ProductStatus.RESTRICTED) ||
                status.equals(ProductStatus.ARCHIVED) ||
                status.equals(ProductStatus.DISCONTINUED);
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String validateAndExtractUser(String jwtToken) throws BadRequestException {
        String username = jwtService.extractUsername(jwtToken);
        if (username == null || username.isEmpty()) {
            throw new BadRequestException("Invalid JWT token: Cannot determine user.");
        }
        return username;
    }
}
