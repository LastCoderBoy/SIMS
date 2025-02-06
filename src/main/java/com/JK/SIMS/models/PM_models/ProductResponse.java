package com.JK.SIMS.models.PM_models;

import java.util.List;

public class ProductResponse {
    private List<ProductsForPM> products;
    private boolean hasAdminAccess;

    public ProductResponse(List<ProductsForPM> products, boolean hasAdminAccess) {
        this.products = products;
        this.hasAdminAccess = hasAdminAccess;
    }

    public List<ProductsForPM> getProducts() {
        return products;
    }

    public void setProducts(List<ProductsForPM> products) {
        this.products = products;
    }

    public boolean isHasAdminAccess() {
        return hasAdminAccess;
    }

    public void setHasAdminAccess(boolean hasAdminAccess) {
        this.hasAdminAccess = hasAdminAccess;
    }
}
