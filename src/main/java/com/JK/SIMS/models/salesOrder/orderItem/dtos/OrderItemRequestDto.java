package com.JK.SIMS.models.salesOrder.orderItem.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequestDto {
    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotNull(message = "SalesOrder quantity is required")
    @Min(value = 1, message = "SalesOrder quantity must be at least 1")
    private Integer quantity;
}
