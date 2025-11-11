package com.JK.SIMS.service.InventoryServices.damageLossService;

import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossRequest;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DamageLossHelper {
    private final Clock clock;

    public boolean isRequestEmpty(DamageLossRequest request) {
        return request.sku() == null &&
                request.lossDate() == null &&
                request.quantityLost() == null &&
                request.reason() == null;
    }

    public PaginatedResponse<DamageLossResponse> transformToPaginatedDTO(Page<DamageLoss> dbResponse) {
        PaginatedResponse<DamageLossResponse> result = new PaginatedResponse<>();
        result.setContent(dbResponse.getContent().stream().map(this::convertToDTO).toList());
        result.setTotalElements(dbResponse.getTotalElements());
        result.setTotalPages(dbResponse.getTotalPages());
        return result;
    }

    public DamageLossResponse convertToDTO(DamageLoss damageLoss) {
        return new DamageLossResponse(
                damageLoss.getId(),
                damageLoss.getIcProduct().getPmProduct().getName(),
                damageLoss.getIcProduct().getPmProduct().getCategory(),
                damageLoss.getIcProduct().getSKU(),
                damageLoss.getQuantityLost(),
                damageLoss.getLossValue(),
                damageLoss.getReason(),
                damageLoss.getLossDate());
    }

    public DamageLoss convertToEntity(DamageLossRequest dto, InventoryControlData inventoryControlData, String user) {
        BigDecimal price = inventoryControlData.getPmProduct().getPrice();
        BigDecimal lossValue = price.multiply(BigDecimal.valueOf(dto.quantityLost()));

        return new DamageLoss(
                null,
                inventoryControlData,
                dto.quantityLost(),
                dto.reason(),
                lossValue,
                dto.lossDate() != null ? dto.lossDate() : LocalDateTime.now(clock),
                user,
                LocalDateTime.now(clock),
                LocalDateTime.now(clock)
        );
    }


    public void validateStockInput(InventoryControlData inventoryProduct, Integer lostQuantity){
        if(inventoryProduct.getCurrentStock() < lostQuantity){
            if(lostQuantity <= 0){
                throw new IllegalArgumentException("Lost level cannot be zero or negative.");
            }
            throw new IllegalArgumentException("Lost level must be lower than or equal to Stock Level.");
        }
    }


    public void validateDamageLossDto(DamageLossRequest dto){
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
}
