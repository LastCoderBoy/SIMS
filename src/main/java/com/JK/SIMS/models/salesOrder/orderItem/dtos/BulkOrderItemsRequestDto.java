package com.JK.SIMS.models.salesOrder.orderItem.dtos;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkOrderItemsRequestDto {
    @NotEmpty(message = "Order items are required")
    List<OrderItemRequestDto> orderItems;
}
