package com.JK.SIMS.models.PM_models;

import com.JK.SIMS.models.IC_models.InventoryData;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "Products_For_Management")
public class ProductsForPM {
    @Id
    @Column(name = "productID", unique = true, nullable = false)
    private String productID;

    @Column(name = "Name")
    private String name;

    // The Location is the location of the product on the shelf.
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
}
