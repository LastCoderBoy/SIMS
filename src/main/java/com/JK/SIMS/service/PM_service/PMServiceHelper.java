package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductsForPM;

import java.math.BigDecimal;

public class PMServiceHelper {
    public static boolean areFieldsValid(ProductsForPM product){
        return  product.getCategory() != null && !product.getCategory().toString().isEmpty() &&
                product.getName() != null && !product.getName().isEmpty() &&
                product.getPrice() != null && !product.getPrice().toString().isEmpty() &&
                product.getLocation() != null && !product.getLocation().isEmpty() &&
                product.getStatus() != null && !product.getStatus().toString().isEmpty();
    }

    public static void validateNewProduct(ProductsForPM product) throws ValidationException {
        if (!PMServiceHelper.areFieldsValid(product)) {
            StringBuilder errorMessage = new StringBuilder("Invalid product data: ");

            if (product.getName() == null || product.getName().trim().isEmpty()) {
                errorMessage.append("Name is required. ");
            }
            if (product.getCategory() == null) {
                errorMessage.append("Category is required. ");
            }
            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errorMessage.append("Valid price is required. ");
            }
            if (product.getLocation() == null || product.getLocation().isEmpty()) {
                errorMessage.append("Location is required. ");
            }
            if(product.getStatus() == null){
                errorMessage.append("Status is required. ");
            }

            throw new ValidationException(errorMessage.toString().trim());
        }
    }
}
