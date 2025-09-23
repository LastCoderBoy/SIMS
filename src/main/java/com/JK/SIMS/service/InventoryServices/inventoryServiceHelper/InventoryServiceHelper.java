package com.JK.SIMS.service.InventoryServices.inventoryServiceHelper;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.email_service.LowStockScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InventoryServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceHelper.class);

    private final LowStockScheduler lowStockAlert;
    @Autowired
    public InventoryServiceHelper(LowStockScheduler lowStockAlert) {
        this.lowStockAlert = lowStockAlert;
    }


    public static void validateUpdateRequest(InventoryData newInventoryData) {
        List<String> errors = new ArrayList<>();
        Integer newCurrentStock = newInventoryData.getCurrentStock();
        Integer newMinLevel = newInventoryData.getMinLevel();

        if (newCurrentStock == null && newMinLevel == null) {
            errors.add("At least one of currentStock or minLevel must be provided");
        }

        if (newCurrentStock != null &&
                newCurrentStock <= 0) {
            errors.add("Current stock must be greater than 0");
        }

        if (newMinLevel != null &&
                newMinLevel <= 0) {
            errors.add("Minimum stock level must be greater than 0");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public void updateInventoryStatus(InventoryData product) {
        if(product.getStatus() != InventoryDataStatus.INVALID) {
            if (product.getCurrentStock() <= product.getMinLevel()) {
                product.setStatus(InventoryDataStatus.LOW_STOCK);
                lowStockAlert.sendDailyLowStockAlert();
            } else {
                product.setStatus(InventoryDataStatus.IN_STOCK);
            }
        }
    }

    public PaginatedResponse<InventoryDataDto> transformToPaginatedDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDto> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream()
                                                        .map(this::convertToDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());
        logger.info("TotalItems (getInventoryDataDTOList): {} products retrieved.", inventoryPage.getContent().size());
        return dtoResponse;
    }

    public InventoryDataDto convertToDTO(InventoryData inventoryData) {
        InventoryDataDto dto = new InventoryDataDto();
        // Set product fields
        dto.setProductID(inventoryData.getPmProduct().getProductID());
        dto.setProductName(inventoryData.getPmProduct().getName());
        dto.setCategory(inventoryData.getPmProduct().getCategory());
        dto.setPrice(inventoryData.getPmProduct().getPrice());
        dto.setProductStatus(inventoryData.getPmProduct().getStatus());

        // Set inventory fields
        dto.setSKU(inventoryData.getSKU());
        dto.setLocation(inventoryData.getLocation());
        dto.setCurrentStock(inventoryData.getCurrentStock());
        dto.setMinLevel(inventoryData.getMinLevel());
        dto.setReservedStock(inventoryData.getReservedStock());
        dto.setInventoryStatus(inventoryData.getStatus());
        dto.setLastUpdate(inventoryData.getLastUpdate().toString());

        return dto;
    }
}
