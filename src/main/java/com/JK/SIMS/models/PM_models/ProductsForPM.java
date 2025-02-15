package com.JK.SIMS.models.PM_models;

import com.JK.SIMS.models.IC_models.InventoryData;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "Products_For_Management")
public class ProductsForPM {
    @Id
    @Column(name = "productID", unique = true, nullable = false)
    private String productID;

    @Column(name = "Name")
    private String name;

    @Column(name = "Location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "Category")
    private ProductCategories category;

    @Column(name = "Price")
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status")
    private ProductStatus status;

    public String getProductID() {
        return productID;
    }

    public void setProductID(String productID) {
        this.productID = productID;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductCategories getCategory() {
        return category;
    }

    public void setCategory(ProductCategories category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
