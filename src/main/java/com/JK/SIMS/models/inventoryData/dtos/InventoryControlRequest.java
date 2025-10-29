package com.JK.SIMS.models.inventoryData.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryControlRequest {
    private Integer currentStock;
    private Integer minLevel;
}
