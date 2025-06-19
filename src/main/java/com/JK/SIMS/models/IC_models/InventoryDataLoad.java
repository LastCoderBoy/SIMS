package com.JK.SIMS.models.IC_models;

import com.JK.SIMS.models.PM_models.ProductCategories;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryDataLoad {

    private InventoryData inventoryData;

    private String productName;

    private ProductCategories category;

}
