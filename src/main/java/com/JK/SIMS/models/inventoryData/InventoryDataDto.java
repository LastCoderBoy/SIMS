package com.JK.SIMS.models.inventoryData;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryDataDto {

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
