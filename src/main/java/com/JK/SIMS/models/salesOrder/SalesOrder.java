package com.JK.SIMS.models.salesOrder;

import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@ToString(exclude = {"items", "qrCode"})
@EqualsAndHashCode(exclude = {"items", "qrCode"})
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, name = "order_reference")
    private String orderReference; // "SO-2024-07-20-001" [SO-date-nextOrderNumberOnThatDay]

    @Column(nullable = false)
    private String destination; // To companies, third parties

    @Column(nullable = false, name = "customer_name")
    private String customerName;

    @Column(name = "created_by")
    private String createdBy; // Person who created the Sales Order.

    @Column(name = "updated_by")
    private String updatedBy; // Person who updated the Sales Order.

    @Column(name = "confirmed_by")
    private String confirmedBy; // Person who is confirming the SalesOrder in the IC.

    @Column(name = "cancelled_by")
    private String cancelledBy; // Person who cancelled the SalesOrder in the IC.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status; // PENDING, PARTIALLY_APPROVED, PARTIALLY_DELIVERED, APPROVED, DELIVERED, COMPLETED, CANCELLED

    @CreationTimestamp
    @Column(updatable = false, name = "order_date")
    private LocalDateTime orderDate;

    @Column(nullable = false, name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdate;

    // ***** Relationships *****
    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "qr_code_id", unique = true)
    private SalesOrderQRCode qrCode;

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

    public void setQrCode(SalesOrderQRCode qrCode) {
        this.qrCode = qrCode;
        qrCode.setSalesOrder(this);
    }

    public boolean isFinalized() {
        return this.status == SalesOrderStatus.CANCELLED || this.status == SalesOrderStatus.DELIVERED
                || this.status == SalesOrderStatus.APPROVED;
    }
}
