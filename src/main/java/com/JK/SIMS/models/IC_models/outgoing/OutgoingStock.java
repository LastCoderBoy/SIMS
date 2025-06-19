package com.JK.SIMS.models.IC_models.outgoing;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "OutgoingStocks")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutgoingStock {

    @Id
    @Column(name = "orderID", unique = true, nullable = false)
    private String orderID;

    @ManyToOne
    @JoinColumn(name = "productID", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column
    private LocalDateTime shippedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutgoingStockStatus status;

    @Column
    private String destination;

    @Column
    private LocalDateTime lastUpdate;
}
