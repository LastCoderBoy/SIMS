package com.JK.SIMS.models.IC_models.salesOrder.dtos.views;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SummarySalesOrderView {
    private Long Id;
    private String orderReference;
    private String destination;
    private SalesOrderStatus status;
    private LocalDateTime orderDate;
    private String customerName;
    private Integer totalItems; // Sum of quantities
    private BigDecimal totalAmount; // Sum of orderPrice * quantity

    public SummarySalesOrderView(SalesOrder salesOrder){
        this.Id = salesOrder.getId();
        this.orderReference = salesOrder.getOrderReference();
        this.destination = salesOrder.getDestination();
        this.status = salesOrder.getStatus();
        this.orderDate = salesOrder.getOrderDate();
        this.customerName = salesOrder.getCustomerName();
        this.totalItems = salesOrder.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        this.totalAmount = salesOrder.getItems().stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
