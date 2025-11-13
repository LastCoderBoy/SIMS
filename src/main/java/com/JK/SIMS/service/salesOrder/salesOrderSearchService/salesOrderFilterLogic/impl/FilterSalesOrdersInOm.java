package com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic.impl;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic.AbstractSoFilterStrategy;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component("filterSalesOrdersInOm")  // Must match field name
public class FilterSalesOrdersInOm extends AbstractSoFilterStrategy {
    protected FilterSalesOrdersInOm(SalesOrderRepository salesOrderRepository) {
        super(salesOrderRepository);
    }

    @Override
    protected @Nullable Specification<SalesOrder> baseSpecType() {
        return null;
    }
}
