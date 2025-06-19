package com.JK.SIMS.models.IC_models.incoming;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "IncomingStock")
public class IncomingStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "productID", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false)
    private Integer quantity;

    @Column
    private LocalDateTime expectedArrivalDate;

    @Column
    private String supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomingStockStatus status;

    @Column
    private LocalDateTime createdDate;

    @Column
    private LocalDateTime lastUpdate;
}
