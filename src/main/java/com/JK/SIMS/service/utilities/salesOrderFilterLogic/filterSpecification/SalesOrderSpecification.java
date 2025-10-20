package com.JK.SIMS.service.utilities.salesOrderFilterLogic.filterSpecification;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class SalesOrderSpecification {
    public static Specification<SalesOrder> byWaitingStatus() {
        return (root, query, criteriaBuilder) ->
                root.get("status").in(
                        SalesOrderStatus.PENDING,
                        SalesOrderStatus.PARTIALLY_DELIVERED,
                        SalesOrderStatus.PARTIALLY_APPROVED
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
}
