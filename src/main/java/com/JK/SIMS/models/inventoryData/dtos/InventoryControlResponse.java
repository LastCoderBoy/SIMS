package com.JK.SIMS.models.inventoryData.dtos;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryControlResponse {

    // Product info
    private String productID;
    private String productName;
    private ProductCategories category;
    private BigDecimal price;
    private ProductStatus productStatus;

    // Inventory info
    private String SKU;
    private String location;
    private int currentStock;
    private int minLevel;
    private int reservedStock;
    private InventoryDataStatus inventoryStatus;
    private String lastUpdate;

}
