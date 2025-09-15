package com.JK.SIMS.models.IC_models.salesOrder;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkShipStockRequestDto {
    @NotNull(message = "Orders list is required")
    List<ShipStockRequestDto> bulkSoRequestDtos;
}
