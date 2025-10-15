package com.JK.SIMS.models.IC_models.salesOrder.dtos.views;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
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
public class DetailedSalesOrderView {
    // Existing fields from SalesOrderView
    private Long Id;
    private String orderReference;
    private String destination;
    private SalesOrderStatus status;
    private LocalDateTime orderDate;
    private String customerName;
    private Integer totalItems; // Sum of quantities
    private BigDecimal totalAmount; // Sum of orderPrice * quantity

    // New fields
    // TODO: Display the OrderItems Status as well
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime deliveryDate;
    private List<OrderItemResponseDto> items; // Nested DTO for OrderItem
    private String confirmedBy;
    private LocalDateTime lastUpdate;

    public DetailedSalesOrderView(SalesOrder salesOrder) {
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

        // Set up New fields
        this.estimatedDeliveryDate = salesOrder.getEstimatedDeliveryDate();
        this.deliveryDate = salesOrder.getDeliveryDate();
        this.items = salesOrder.getItems().stream()
                .map(item -> new OrderItemResponseDto(
                        item.getId(),
                        item.getProduct().getProductID(),
                        item.getProduct().getName(),
                        item.getProduct().getCategory(),
                        item.getQuantity(),
                        item.getProduct().getPrice(),
                        item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                )).toList();
        this.confirmedBy = salesOrder.getConfirmedBy();
        this.lastUpdate = salesOrder.getLastUpdate();
    }
}
