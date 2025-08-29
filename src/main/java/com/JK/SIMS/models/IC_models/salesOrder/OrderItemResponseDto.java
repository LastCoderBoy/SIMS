package com.JK.SIMS.models.IC_models.salesOrder;

import com.JK.SIMS.models.PM_models.ProductCategories;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponseDto {
    private Long id;
    private String productId;
    private String productName;
    private ProductCategories productCategory;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}

