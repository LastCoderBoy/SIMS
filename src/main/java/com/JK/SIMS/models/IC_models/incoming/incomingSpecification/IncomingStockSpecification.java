package com.JK.SIMS.models.IC_models.incoming.incomingSpecification;

import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class IncomingStockSpecification {

    public static Specification<IncomingStock> hasStatus(IncomingStockStatus status){
        return (root, query, criteriaBuilder) -> {
            if(status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<IncomingStock> hasProductCategory(ProductCategories category) {
        return (root, query, criteriaBuilder) -> {
            if (category == null) return null;

            // Join with product entity to access category
            Join<IncomingStock, ProductsForPM> productJoin = root.join("product");
            return criteriaBuilder.equal(productJoin.get("category"), category);

            // SAME AS THE DOWN BELOW

            //return cb.equal(root.get("product").get("category"), category);
        };
    }
}
