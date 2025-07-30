package com.JK.SIMS.models.IC_models;

import com.JK.SIMS.models.IC_models.outgoing.OrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryPageResponse {
    private Long totalInventorySize;
    
    private Long lowStockSize;
    
    private Long incomingStockSize;

    private Long outgoingStockSize;

    private Long damageLossSize;

    private PaginatedResponse<OrderResponseDto> allPendingOrders;
}
