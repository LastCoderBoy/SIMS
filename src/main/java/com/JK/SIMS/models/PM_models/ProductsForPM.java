package com.JK.SIMS.models.PM_models;

import com.JK.SIMS.models.ProductCategories;
import com.JK.SIMS.models.ProductStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "Products_For_Management")
public class ProductsForPM {
    @Id
    @Column(name = "productID", unique = true, nullable = false)
    private String productID;

    @Column(name = "Name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "Category")
    private ProductCategories category;

    @Column(name = "Stock")
    private Long stock;

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

    public Long getStock() {
        return stock;
    }

    public void setStock(Long stock) {
        this.stock = stock;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
