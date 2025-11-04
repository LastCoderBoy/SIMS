package com.JK.SIMS.models.PM_models.dtos;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProductManagementRequest {
    private String name;
    private String location;
    private ProductCategories category;
    private BigDecimal price;
    private ProductStatus status;
}
