package com.JK.SIMS.models.IC_models;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
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
    private InventoryControlStatus status;

    @Column(name = "LastUpdate")
    private LocalDateTime lastUpdate;


    public String getSKU() {
        return SKU;
    }

    public void setSKU(String SKU) {
        this.SKU = SKU;
    }

    public ProductsForPM getProduct() {
        return product;
    }

    public void setProduct(ProductsForPM product) {
        this.product = product;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(Integer minLevel) {
        this.minLevel = minLevel;
    }

    public InventoryControlStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryControlStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
