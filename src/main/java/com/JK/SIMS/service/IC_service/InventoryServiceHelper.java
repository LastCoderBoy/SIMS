package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossDTO;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossDTORequest;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossMetrics;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;

import java.util.ArrayList;
import java.util.List;

public class InventoryServiceHelper {

    protected static void validateUpdateRequest(InventoryData newInventoryData) {
        List<String> errors = new ArrayList<>();

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

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    protected static void nullCheckValidation(DamageLossDTORequest dto){
        List<String> errors = new ArrayList<>();
        if(dto != null){
            if(dto.sku() == null){
                errors.add("SKU cannot be null.");
            }
            if(dto.quantityLost() == null){
                errors.add("Lost quantity cannot be null.");
            }
            if(dto.reason() == null){
                errors.add("Reason cannot be null.");
            }
        }
        if(dto == null){
            throw new ValidationException("DL (addDamageLoss): DTO cannot be null.");
        }

        if(!errors.isEmpty()){
            throw new ValidationException(errors);
        }
    }

    protected static void updateInventoryStatus(InventoryData product) {
        if (product.getCurrentStock() <= product.getMinLevel()) {
            product.setStatus(InventoryDataStatus.LOW_STOCK);
        } else {
            product.setStatus(InventoryDataStatus.IN_STOCK);
        }
    }
}
