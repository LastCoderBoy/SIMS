package com.JK.SIMS.models.PM_models;

public enum ProductStatus {
    ACTIVE, // Product is ready to sell

    ON_ORDER, // Product being considered is accepted, supplier is confirmed the order

    PLANNING, // Product is being considered to sell


     //  ******* INVALID statuses *******

    DISCONTINUED, // Product is stopped but present in the Inventory

    ARCHIVED, // Product has been sold before and may or may not be present in the inventory

    RESTRICTED // Product is restricted due to some reasons, might be present in the Inventory or not. Information for future plans
}
