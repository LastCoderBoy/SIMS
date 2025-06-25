package com.JK.SIMS.service;

import com.JK.SIMS.models.PM_models.ProductStatus;

public class GlobalServiceHelper {

    public static boolean amongInvalidStatus(ProductStatus status) {
        return status.equals(ProductStatus.RESTRICTED) ||
                status.equals(ProductStatus.ARCHIVED) ||
                status.equals(ProductStatus.DISCONTINUED);
    }
}
