package com.JK.SIMS.models.IC_models.salesOrder.dtos;

import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.OrderItemRequestDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SalesOrderRequestDto {
    @NotBlank(message = "Destination is required")
    private String destination;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotEmpty
    private List<OrderItemRequestDto> orderItems;
}
