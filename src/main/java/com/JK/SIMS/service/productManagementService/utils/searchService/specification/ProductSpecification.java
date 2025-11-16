package com.JK.SIMS.service.productManagementService.utils.searchService.specification;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class ProductSpecification {

    public static Specification<ProductsForPM> hasCategory(ProductCategories category) {
        return (root, query, criteriaBuilder) -> {
            if (category == null) return null;
            return criteriaBuilder.equal(root.get("category"), category);
        };
    }

    public static Specification<ProductsForPM> hasLocation(String location) {
        return (root, query, criteriaBuilder) -> {
            if (location == null || location.trim().isEmpty()) return null;
            return criteriaBuilder.equal(root.get("location"), location);
        };
    }

    public static Specification<ProductsForPM> hasPriceLessThanOrEqual(BigDecimal price) {
        return (root, query, criteriaBuilder) -> {
            if (price == null) return null;
            return criteriaBuilder.lessThanOrEqualTo(root.get("price"), price);
        };
    }

    public static Specification<ProductsForPM> hasStatus(ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }


    // For the general filter (location, category, or status)
    public static Specification<ProductsForPM> generalFilter(String filter) {
        return (root, query, criteriaBuilder) -> {
            if (filter == null || filter.trim().isEmpty()) return null;

            String searchPattern = "%" + filter.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("category").as(String.class)), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("status").as(String.class)), searchPattern)
            );
        };
    }
}
