package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.PaginatedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InventoryServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceHelper.class);

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

    protected static void updateInventoryStatus(InventoryData product) {
        if (product.getCurrentStock() <= product.getMinLevel()) {
            product.setStatus(InventoryDataStatus.LOW_STOCK);
        } else {
            product.setStatus(InventoryDataStatus.IN_STOCK);
        }
    }

    public PaginatedResponse<InventoryDataDto> transformToPaginatedDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDto> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream().map(this::convertToDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());
        logger.info("TotalItems (getInventoryDataDTOList): {} products retrieved.", inventoryPage.getContent().size());
        return dtoResponse;
    }

    public InventoryDataDto convertToDTO(InventoryData currentProduct) {
        InventoryDataDto inventoryDataDTO = new InventoryDataDto();
        inventoryDataDTO.setInventoryData(currentProduct);
        inventoryDataDTO.setProductName(currentProduct.getPmProduct().getName());
        inventoryDataDTO.setCategory(currentProduct.getPmProduct().getCategory());
        return inventoryDataDTO;
    }
}
