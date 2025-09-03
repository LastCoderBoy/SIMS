package com.JK.SIMS.models.IC_models.inventoryData;

import com.JK.SIMS.models.PM_models.ProductCategories;
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
    private String productStatus;

    // Inventory info
    private String SKU;
    private String location;
    private int currentStock;
    private int minLevel;
    private int reservedStock;
    private InventoryDataStatus status;
    private String lastUpdate;

}
