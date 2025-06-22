package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.InventoryData;

import java.util.ArrayList;
import java.util.List;

public class InventoryServiceHelper {

    protected static void validateUpdateRequest(String sku, InventoryData newInventoryData) {
        List<String> errors = new ArrayList<>();

        if (sku == null || sku.trim().isEmpty()) {
            errors.add("SKU cannot be null or empty");
        }

        if (newInventoryData == null) {
            errors.add("Update data cannot be null");
        } else {

            if (newInventoryData.getCurrentStock() == null && newInventoryData.getMinLevel() == null) {
                errors.add("At least one of currentStock or minLevel must be provided");
            }

            if (newInventoryData.getCurrentStock() != null &&
                    newInventoryData.getCurrentStock() <= 0) {
                errors.add("Current stock must be greater than 0");
            }

            if (newInventoryData.getMinLevel() != null &&
                    newInventoryData.getMinLevel() <= 0) {
                errors.add("Minimum stock level must be greater than 0");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

}
