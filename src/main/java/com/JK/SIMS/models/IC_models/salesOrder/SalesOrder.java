package com.JK.SIMS.models.IC_models.salesOrder;

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
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderReference; // "SO-2024-07-20-001" [SO-date-nextOrderNumberOnThatDay]

    @Column(nullable = false)
    private String destination; // To companies, third parties

    @Column(nullable = false)
    private String customerName;

    @Column
    private String confirmedBy; // Person who is confirming the SalesOrder in the IC.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status; // PENDING, PROCESSING, SHIPPED, COMPLETED, CANCELLED

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime orderDate;

    @UpdateTimestamp
    private LocalDateTime lastUpdate;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public SalesOrder(String orderReference, String destination, SalesOrderStatus status, List<OrderItem> items){
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
        item.setSalesOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        items.remove(item);
        item.setSalesOrder(null);
    }
}
