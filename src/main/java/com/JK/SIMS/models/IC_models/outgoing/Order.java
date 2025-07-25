package com.JK.SIMS.models.IC_models.outgoing;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderReference; // "ORD-2024-07-20-001" [ORD-data-nextOrderNumberOnThatDay]

    @Column(nullable = false)
    private String destination; // To companies, third parties

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // PROCESSING, SHIPPED, COMPLETED, CANCELLED

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime orderDate;

    @UpdateTimestamp
    private LocalDateTime lastUpdate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(String orderReference, String destination, OrderStatus status, List<OrderItem> items){
        this.orderReference = orderReference;
        this.destination = destination;
        this.status = status;
        if (items != null) {
            for (OrderItem item : items) {
                this.addOrderItem(item);       // Add items using the helper method to set the bidirectional link
            }
        }
    }

    // Add this helper method:
    public void addOrderItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}
