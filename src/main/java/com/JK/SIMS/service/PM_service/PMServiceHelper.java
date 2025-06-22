package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.service.IC_service.InventoryControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PMServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(PMServiceHelper.class);
    private static final Set<String> VALID_SORT_OPTIONS = Set.of("lowtohighprice", "hightolowprice", "lowtohighbyid", "hightolowbyid");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[A-Za-z]\\d{1,2}-\\d{3}$");

    public static boolean areFieldsValid(ProductsForPM product){
        return  product.getCategory() != null && !product.getCategory().toString().isEmpty() &&
                product.getName() != null && !product.getName().isEmpty() &&
                product.getPrice() != null && !product.getPrice().toString().isEmpty() &&
                product.getLocation() != null && !product.getLocation().isEmpty() &&
                product.getStatus() != null && !product.getStatus().toString().isEmpty();
    }

    public static boolean validateNewProduct(ProductsForPM product) throws ValidationException {
        if (!areFieldsValid(product)) {
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
            else {
                if (!LOCATION_PATTERN.matcher(product.getLocation()).matches()) {
                    errorMessage.append("Invalid location format. Expected format: section-shelf (e.g., A1-101). ");
                }
            }
            if(product.getStatus() == null){
                errorMessage.append("Status is required. ");
            }

            throw new ValidationException(errorMessage.toString().trim());
        }
        return true;
    }

    public static void validateInputs(String category, String sortBy, String status) {
        if (category != null) {
            try {
                ProductCategories.valueOf(category.toUpperCase().trim());
            } catch (ValidationException e) {
                throw new ValidationException("Invalid category: " + category);
            }
        }

        if (status != null) {
            try {
                ProductStatus.valueOf(status.toUpperCase().trim());
            } catch (ValidationException e) {
                throw new ValidationException("Invalid status: " + status);
            }
        }

        if (sortBy != null && !VALID_SORT_OPTIONS.contains(sortBy.toLowerCase().trim())) {
            throw new ValidationException("Invalid sortBy parameter: " + sortBy);
        }
    }


    public static void validateLocationFormat(String location) {
        Pattern locationPattern = Pattern.compile("^[A-Za-z]\\d{1,2}-\\d{3}$");
        if (!locationPattern.matcher(location).matches()) {
            throw new ValidationException("PM (updateProduct): Invalid location format.");
        }
    }

    public static int extractIdNumber(String productID){
        try{
            if (productID == null){
                return 0;
            }
            // productID => PRD123. PRD part will be removed and the number will be returned as an integer.
            return Integer.parseInt(productID.replaceAll("\\D", ""));
        }catch (NumberFormatException nfe){
            return 0;
        }
    }
}
