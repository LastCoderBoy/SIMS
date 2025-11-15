package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils;

import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.service.email_service.LowStockScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryStatusModifier {

    private final LowStockScheduler lowStockAlert;

    /**
     * Updates inventory status based on current stock levels
     * Business logic: Determines if stock is low and triggers alerts
     *
     * @param inventory The inventory item to update
     */
    @Transactional
    public void updateInventoryStatus(InventoryControlData inventory) {
        if(inventory.getStatus() != InventoryDataStatus.INVALID) {
            if (inventory.getCurrentStock() <= inventory.getMinLevel()) {
                inventory.setStatus(InventoryDataStatus.LOW_STOCK);
                lowStockAlert.sendDailyLowStockAlert();
            } else {
                inventory.setStatus(InventoryDataStatus.IN_STOCK);
            }
        }
    }
}
