package com.JK.SIMS.models.salesOrder.dtos.processSalesOrderDtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessSalesOrderRequestDto {
    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Item quantities are required")
    private Map<String, Integer> itemQuantities; // ProductID: Shipped quantity
}
