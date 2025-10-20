package com.JK.SIMS.models.purchaseOrder.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmPoRequestDto {
    @NotNull(message = "Expected arrival date is required")
    @FutureOrPresent(message = "Arrival date must be today or in the future")
    private LocalDate expectedArrivalDate;
}