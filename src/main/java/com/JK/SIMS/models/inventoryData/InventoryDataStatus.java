package com.JK.SIMS.models.inventoryData;

public enum InventoryDataStatus {
    INCOMING,
    OUTGOING,
    IN_STOCK,
    LOW_STOCK,
    INVALID // This will be used to indicate that the product is not for sale due to RESTRICTED, ARCHIVED, or DISCONTINUED
}
