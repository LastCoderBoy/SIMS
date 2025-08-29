package com.JK.SIMS.models.IC_models.inventoryData;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryPageResponse {
    private Long totalInventorySize;
    
    private Long lowStockSize;
    
    private Long incomingStockSize;

    private Long outgoingStockSize;

    private Long damageLossSize;

    private PaginatedResponse<SalesOrderResponseDto> allPendingOrders;
}
