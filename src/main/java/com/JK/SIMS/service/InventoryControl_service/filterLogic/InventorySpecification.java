package com.JK.SIMS.service.InventoryControl_service.filterLogic;

import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class InventorySpecification {
    public static Specification<InventoryData> hasStatus(InventoryDataStatus status){
        return ((root, query, criteriaBuilder) -> {
            if(status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        });
    }
    public static Specification<InventoryData> hasProductCategory(ProductCategories category) {
        return ((root, query, criteriaBuilder) -> {
            if (category == null) return null;
            Join<InventoryData, ProductsForPM> joinProduct = root.join("pmProduct");
            return criteriaBuilder.equal(joinProduct.get("category"), category);
        });
    }
}
