package com.JK.SIMS.models.IC_models.purchaseOrder;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderRequestDto {
    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotNull(message = "Ordered quantity is required")
    @Min(value = 1, message = "SalesOrder quantity must be at least 1")
    private Integer orderQuantity;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotNull(message = "Expected arrival date is required")
    @FutureOrPresent(message = "Arrival date must be today or in the future")
    private LocalDate expectedArrivalDate;

    // Optional field, and better info in Email. And will show up when the status is on Cancelled and Failed
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
