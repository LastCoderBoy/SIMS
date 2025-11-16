package com.JK.SIMS.service.productManagementService.utils;

import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Pure utility class for Product Management operations
 * Contains ONLY stateless transformation and validation logic
 * NO database access - keeps helper lightweight and reusable
 */
@Component
@Slf4j
public class PMServiceHelper {

    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[A-Za-z]\\d{1,2}-\\d{3}$");

    /**
     * Validates a product entity by checking all required fields and their formats.
     *
     * @param product The product entity to validate
     * @return true if validation passes
     * @throws ValidationException if any validation rule is violated, with detailed error message
     */
    public boolean validateProduct(ProductManagementRequest product) throws ValidationException {
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
                errorMessage.append("Invalid location format. Expected format: section-shelf (e.g., A1-101).");
            }
        }

        if (!errorMessage.isEmpty()) {
            throw new ValidationException("Invalid product data: " + errorMessage.toString().trim());
        }

        return true;
    }

    public boolean isAllFieldsNull(ProductManagementRequest product) {
        return product.getName() == null &&
                product.getCategory() == null &&
                product.getPrice() == null &&
                product.getStatus() == null &&
                product.getLocation() == null;
    }


    public boolean validateStatusBeforeAdding(ProductStatus currentStatus, ProductStatus newStatus){
        if(currentStatus.equals(ProductStatus.PLANNING) || currentStatus.equals(ProductStatus.ARCHIVED)){
            return !newStatus.equals(ProductStatus.PLANNING);
        }
        return false;
    }

    public void validateLocationFormat(String location) {
        if (!LOCATION_PATTERN.matcher(location).matches()) {
            throw new ValidationException("PM (updateProduct): Invalid location format. Expected format: section-shelf (e.g., A1-101). ");
        }
    }

    public ProductManagementResponse convertToDTO(ProductsForPM product) {
        return new ProductManagementResponse(
                product.getProductID(),
                product.getName(),
                product.getLocation(),
                product.getCategory(),
                product.getPrice(),
                product.getStatus()
        );
    }

    public PaginatedResponse<ProductManagementResponse> transformToPaginatedResponse(Page<ProductsForPM> products) {
        PaginatedResponse<ProductManagementResponse> response = new PaginatedResponse<>();
        response.setContent(products.getContent().stream().map(this::convertToDTO).toList());
        response.setTotalPages(products.getTotalPages());
        response.setTotalElements(products.getTotalElements());
        return response;
    }

    public ProductsForPM createProductEntity(ProductManagementRequest request) {
        ProductsForPM product = new ProductsForPM();
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setLocation(request.getLocation());
        product.setStatus(request.getStatus());
        return product;
    }
}
