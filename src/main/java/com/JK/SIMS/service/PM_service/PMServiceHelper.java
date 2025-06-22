package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.service.IC_service.InventoryControlService;
import com.JK.SIMS.service.IC_service.InventoryServiceHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

public class PMServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(PMServiceHelper.class);
    private static final Set<String> VALID_SORT_OPTIONS = Set.of("lowtohighprice", "hightolowprice", "lowtohighbyid", "hightolowbyid");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[A-Za-z]\\d{1,2}-\\d{3}$");


    /**
     * Validates a product entity by checking all required fields and their formats.
     *
     * @param product The product entity to validate
     * @return true if validation passes
     * @throws ValidationException if any validation rule is violated, with detailed error message
     */
    protected static boolean validateProduct(ProductsForPM product) throws ValidationException {
        StringBuilder errorMessage = new StringBuilder();

        if (product.getName() == null || product.getName().trim().isEmpty()) {
            errorMessage.append("Name is required. ");
        }
        if (product.getCategory() == null) {
            errorMessage.append("Category is required. ");
        }
        if (product.getStatus() == null) {
            errorMessage.append("Status is required. ");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errorMessage.append("Valid price is required. ");
        }
        if (product.getLocation() == null || product.getLocation().isEmpty()) {
            errorMessage.append("Location is required. ");
        } else {
            if (!LOCATION_PATTERN.matcher(product.getLocation()).matches()) {
                errorMessage.append("Invalid location format. Expected format: section-shelf (e.g., A1-101). ");
            }
        }

        if (!errorMessage.isEmpty()) {
            throw new ValidationException("Invalid product data: " + errorMessage.toString().trim());
        }

        return true;
    }

    protected static boolean isAllFieldsNull(ProductsForPM product) {
        return product.getName() == null &&
                product.getCategory() == null &&
                product.getPrice() == null &&
                product.getStatus() == null &&
                product.getLocation() == null;
    }


    protected static void validateLocationFormat(String location) {
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
