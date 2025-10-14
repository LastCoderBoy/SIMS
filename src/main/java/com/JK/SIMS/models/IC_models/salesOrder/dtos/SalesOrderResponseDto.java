package com.JK.SIMS.models.IC_models.salesOrder.dtos;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.OrderItemResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderResponseDto {
    private Long id;
    private String orderReference;
    private String destination;
    private String customerName; // Coming from the ProductsForPM model. (SalesOrder -> OrderItem -> ProductsForPM)
    private SalesOrderStatus status;
    private LocalDateTime orderDate;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime deliveryDate;
    private LocalDateTime lastUpdate;
    private BigDecimal totalAmount;
    private List<OrderItemResponseDto> items;
}

