package com.JK.SIMS.models.IC_models.inventoryData;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "InventoryControl_Data")
public class InventoryControlData {

    @Id
    @Column(name = "SKU", unique = true, nullable = false)
    private String SKU;

    @ManyToOne
    @JoinColumn(name = "productID", nullable = false)
    private ProductsForPM pmProduct;

    @Column(name = "Location")
    private String location; // Location of the product in the factory shelf

    @Column(name = "CurrentStock")
    private Integer currentStock;

    @Column(name = "MinLevel")
    private Integer minLevel;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock = 0;  // Once the Customer orders a product

    @Enumerated(EnumType.STRING)
    @Column(name = "Status")
    private InventoryDataStatus status;

    @Column(name = "LastUpdate")
    @UpdateTimestamp
    private LocalDateTime lastUpdate;
}
