package com.JK.SIMS.service.PM_service;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PMServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(PMServiceHelper.class);
    private static final Set<String> VALID_SORT_OPTIONS = Set.of("lowtohighprice", "hightolowprice", "lowtohighbyid", "hightolowbyid");

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

    public static void sortProducts(List<ProductsForPM> products, String sortBy) {
        switch (sortBy.toLowerCase().trim()) {
            case "lowtohighprice":
                products.sort(Comparator.comparing(ProductsForPM::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                logger.info("PM: Sorted {} products by price (ascending)", products.size());
                break;
            case "hightolowprice":
                products.sort(Comparator.comparing(ProductsForPM::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                logger.info("PM: Sorted {} products by price (descending)", products.size());
                break;
            case "lowtohighbyid":
                // No need to sort as the list is already in ascending order
                logger.info("PM: Products are already sorted by ID (ascending)");
                break;
            case "hightolowbyid":
                // Reverse the list for descending order
                Collections.reverse(products);
                logger.info("PM: Sorted {} products by ID (descending)", products.size());
                break;

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
