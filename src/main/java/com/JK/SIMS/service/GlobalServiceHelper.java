package com.JK.SIMS.service;

import com.JK.SIMS.models.PM_models.ProductStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class GlobalServiceHelper {

    public static boolean amongInvalidStatus(ProductStatus status) {
        return status.equals(ProductStatus.RESTRICTED) ||
                status.equals(ProductStatus.ARCHIVED) ||
                status.equals(ProductStatus.DISCONTINUED);
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
