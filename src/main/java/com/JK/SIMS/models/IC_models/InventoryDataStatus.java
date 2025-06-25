package com.JK.SIMS.models.IC_models;

public enum InventoryDataStatus {
    INCOMING,
    OUTGOING,
    DAMAGE_LOSS, // This will be completely one separate section inside the IC section
    IN_STOCK,
    LOW_STOCK,
    INVALID // This will be used to indicate that the product is not for sale due to RESTRICTED, ARCHIVED, or DISCONTINUED
}
