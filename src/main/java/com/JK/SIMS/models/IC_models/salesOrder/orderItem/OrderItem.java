package com.JK.SIMS.models.IC_models.salesOrder.orderItem;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "salesOrder")
@EqualsAndHashCode(exclude = "salesOrder")
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer approvedQuantity = 0; // track the quantity that has been fulfilled

    @Column(nullable = false)
    private BigDecimal orderPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus status = OrderItemStatus.PENDING; // Default

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    public OrderItem(Integer quantity, ProductsForPM product, BigDecimal totalOrderPrice){
        this.quantity = quantity;
        this.product = product;
        this.orderPrice = totalOrderPrice;
    }

    public boolean isFinalized(){
        return this.status == OrderItemStatus.APPROVED;
    }
}
