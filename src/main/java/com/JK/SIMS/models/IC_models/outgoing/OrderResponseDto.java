package com.JK.SIMS.models.IC_models.outgoing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponseDto {
    private Long id;
    private String orderReference;
    private String destination;
    private String customerName;
    private OrderStatus status;
    private LocalDateTime orderDate;
    private LocalDateTime lastUpdate;
    private BigDecimal totalAmount;
    private List<OrderItemResponseDto> items;
}

