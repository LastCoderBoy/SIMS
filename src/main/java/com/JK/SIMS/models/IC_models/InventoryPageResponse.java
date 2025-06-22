package com.JK.SIMS.models.IC_models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryPageResponse {
    private Integer totalInventorySize;
    
    private Integer lowStockSize;
    
    private Integer incomingStockSize;

    private Integer outgoingStockSize;

    private Integer damageLossSize;

    private List<InventoryDataDTO> inventoryDataDTOList;
}
