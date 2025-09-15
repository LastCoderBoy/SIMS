package com.JK.SIMS.models.IC_models.salesOrder;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShipStockRequestDto {
    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Item quantities are required")
    private Map<Long, Integer> itemQuantities; // Key: OrderItem ID, Value: Shipped quantity

}
