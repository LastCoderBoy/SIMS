package com.JK.SIMS.models.PM_models;

public enum ProductStatus {
    ACTIVE, // Product is ready to sell

    DISCONTINUED, // Product is stopped but present in the Inventory

    ARCHIVED, // Product has been sold before and may or may not be present in the inventory

    PLANNING, // Product is being considered to sell

    RESTRICTED, // Product is restricted due to some reasons, might be present in the Inventory or not. Information for future plans

    ON_ORDER // Product being considered is accepted, ordered from a supplier or not yet ordered.
}
