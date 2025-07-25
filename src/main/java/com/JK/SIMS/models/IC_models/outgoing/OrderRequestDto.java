package com.JK.SIMS.models.IC_models.outgoing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OrderRequestDto {
    @NotBlank(message = "Destination is required")
    private String destination;

    @NotEmpty
    private List<OrderItemDto> items;
}
