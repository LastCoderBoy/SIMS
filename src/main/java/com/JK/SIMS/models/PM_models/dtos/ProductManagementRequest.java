package com.JK.SIMS.models.PM_models.dtos;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProductManagementRequest {
    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Location is required")
    @Pattern(regexp = "^[A-Z]{1,2}-\\d{1,3}$", message = "Location must follow format: A-123 or AB-12")
    private String location;

    @NotNull(message = "Category is required")
    private ProductCategories category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "99999.99", message = "Price cannot exceed 99999.99")
    @Digits(integer = 5, fraction = 2, message = "Price must have at most 5 digits and 2 decimal places")
    private BigDecimal price;

    @NotNull(message = "Status is required")
    private ProductStatus status;
}
