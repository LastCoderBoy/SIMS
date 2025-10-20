package com.JK.SIMS.models.purchaseOrder.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReceiveStockRequestDto {

    @NotNull(message = "Received quantity is required")
    @Min(value = 0, message = "Received quantity must be at least 0")
    private Integer receivedQuantity;

    private LocalDate actualArrivalDate;
}
