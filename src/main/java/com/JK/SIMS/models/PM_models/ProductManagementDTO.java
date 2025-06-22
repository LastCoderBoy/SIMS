package com.JK.SIMS.models.PM_models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductManagementDTO {
    private String productID;
    private String name;
    private String location;
    private ProductCategories category;
    private BigDecimal price;
    private ProductStatus status;
}
