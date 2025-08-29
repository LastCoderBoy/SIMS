package com.JK.SIMS.models.IC_models.purchaseOrder.purchaseOrderSpec;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class PurchaseOrderSpecification {

    public static Specification<PurchaseOrder> hasStatus(PurchaseOrderStatus status){
        return (root, query, criteriaBuilder) -> {
            if(status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<PurchaseOrder> hasProductCategory(ProductCategories category) {
        return (root, query, criteriaBuilder) -> {
            if (category == null) return null;

            // Join with product entity to access category
            Join<PurchaseOrder, ProductsForPM> productJoin = root.join("product");
            return criteriaBuilder.equal(productJoin.get("category"), category);

            // SAME AS THE DOWN BELOW

            //return cb.equal(root.get("product").get("category"), category);
        };
    }
}
