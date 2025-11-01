package com.JK.SIMS.models.salesOrder.dtos;


import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderStatusRequest {
    @NotNull(message = "Status is required")
    private SalesOrderStatus status;
}
