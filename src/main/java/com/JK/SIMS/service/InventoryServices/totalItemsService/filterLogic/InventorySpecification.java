package com.JK.SIMS.service.InventoryServices.totalItemsService.filterLogic;

import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class InventorySpecification {
    public static Specification<InventoryControlData> hasStatus(InventoryDataStatus status){
        return ((root, query, criteriaBuilder) -> {
            if(status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        });
    }
    public static Specification<InventoryControlData> hasProductCategory(ProductCategories category) {
        return ((root, query, criteriaBuilder) -> {
            if (category == null) return null;
            Join<InventoryControlData, ProductsForPM> joinProduct = root.join("pmProduct");
            return criteriaBuilder.equal(joinProduct.get("category"), category);
        });
    }
}
