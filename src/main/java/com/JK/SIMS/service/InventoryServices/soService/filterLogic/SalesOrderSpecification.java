package com.JK.SIMS.service.InventoryServices.soService.filterLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class SalesOrderSpecification {
    public static Specification<SalesOrder> byWaitingStatus() {
        return (root, query, criteriaBuilder) ->
                root.get("status").in(
                        SalesOrderStatus.PENDING,
                        SalesOrderStatus.APPROVED
                );
    }

    public static Specification<SalesOrder> byStatus(SalesOrderStatus status){
        return (root, query, criteriaBuilder) ->  {
            if(status == null) return null;
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<SalesOrder> byDatesBetween(String optionDate, LocalDate startDate, LocalDate endDate){
        return (root, query, criteriaBuilder) -> {
            if (optionDate == null || optionDate.isEmpty()) return null;
            return switch (optionDate.toLowerCase()) {
                case "orderdate" -> criteriaBuilder.between(root.get("orderDate"), startDate, endDate);
                case "deliverydate" -> criteriaBuilder.between(root.get("deliveryDate"), startDate, endDate);
                case "estimateddeliverydate" ->
                        criteriaBuilder.between(root.get("estimatedDeliveryDate"), startDate, endDate);
                default -> null;
            };
        };
    }

    public static Specification<SalesOrder> hasProductCategory(ProductCategories category) {
        return ((root, query, criteriaBuilder) -> {
            if (category == null) return null;
            // Join SalesOrder -> OrderItem -> ProductsForPM
            Join<SalesOrder, OrderItem> itemJoin = root.join("items");
            Join<OrderItem, ProductsForPM> productJoin = itemJoin.join("product");

            return criteriaBuilder.equal(productJoin.get("category"), category);
        });
    }
}
