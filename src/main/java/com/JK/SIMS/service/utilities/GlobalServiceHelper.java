package com.JK.SIMS.service.utilities;

import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.service.userManagement_service.JWTService;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class GlobalServiceHelper {

    public final JWTService jwtService;
    @Autowired
    public GlobalServiceHelper(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    public static boolean amongInvalidStatus(ProductStatus status) {
        return status.equals(ProductStatus.RESTRICTED) ||
                status.equals(ProductStatus.ARCHIVED) ||
                status.equals(ProductStatus.DISCONTINUED);
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public String validateAndExtractUser(String jwtToken) throws BadRequestException {
        String username = jwtService.extractUsername(jwtToken);
        if (username == null || username.isEmpty()) {
            throw new BadRequestException("Invalid JWT token: Cannot determine user.");
        }
        return username;
    }

    public void validatePaginationParameters(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    public static <T extends Enum<T>> boolean isInEnum(String value, Class<T> enumClass) {
        try {
            Enum.valueOf(enumClass, value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
