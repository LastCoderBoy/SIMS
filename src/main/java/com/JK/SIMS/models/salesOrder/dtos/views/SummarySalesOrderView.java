package com.JK.SIMS.models.salesOrder.dtos.views;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
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
    private LocalDateTime estimatedDeliveryDate;
    private String customerName;
    private Integer totalOrderedQuantity; // Sum of ordered quantities
    private BigDecimal totalAmount; // Sum of orderPrice * quantity
    private Integer totalApprovedQuantity;

    public SummarySalesOrderView(SalesOrder salesOrder){
        this.Id = salesOrder.getId();
        this.orderReference = salesOrder.getOrderReference();
        this.destination = salesOrder.getDestination();
        this.status = salesOrder.getStatus();
        this.orderDate = salesOrder.getOrderDate();
        this.estimatedDeliveryDate = salesOrder.getEstimatedDeliveryDate();
        this.customerName = salesOrder.getCustomerName();
        this.totalOrderedQuantity = salesOrder.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        this.totalAmount = salesOrder.getItems().stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalApprovedQuantity = salesOrder.getItems().stream().mapToInt(OrderItem::getApprovedQuantity).sum();
    }
}
