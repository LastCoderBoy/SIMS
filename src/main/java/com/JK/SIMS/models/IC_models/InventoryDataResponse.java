package com.JK.SIMS.models.IC_models;

import java.time.LocalDateTime;

public class InventoryDataResponse {

    private String SKU;

    private String productName;

    private String location;

    private Integer currentStock;

    private Integer minLevel;

    private InventoryControlStatus status;

    private LocalDateTime lastUpdate;

    public InventoryDataResponse() {
    }

    public InventoryDataResponse(String SKU, String productName, String location, Integer currentStock, Integer minLevel, InventoryControlStatus status, LocalDateTime lastUpdate) {
        this.SKU = SKU;
        this.productName = productName;
        this.location = location;
        this.currentStock = currentStock;
        this.minLevel = minLevel;
        this.status = status;
        this.lastUpdate = lastUpdate;
    }

    public String getSKU() {
        return SKU;
    }

    public void setSKU(String SKU) {
        this.SKU = SKU;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
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
