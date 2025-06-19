package com.JK.SIMS.models.IC_models;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "InventoryControl_Data")
public class InventoryData {

    @Id
    @Column(name = "SKU", unique = true, nullable = false)
    private String SKU;

    @ManyToOne
    @JoinColumn(name = "productID", nullable = false)
    private ProductsForPM product;

    @Column(name = "Location")
    private String location;

    @Column(name = "CurrentStock")
    private Integer currentStock;

    @Column(name = "MinLevel")
    private Integer minLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status")
    private InventoryDataStatus status;

    @Column(name = "LastUpdate")
    private LocalDateTime lastUpdate;
}
