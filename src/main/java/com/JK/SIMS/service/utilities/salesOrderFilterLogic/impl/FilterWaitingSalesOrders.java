package com.JK.SIMS.service.utilities.salesOrderFilterLogic.impl;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.AbstractSoFilterStrategy;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.filterSpecification.SalesOrderSpecification;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component("filterWaitingSalesOrders") // Must match field name
public class FilterWaitingSalesOrders extends AbstractSoFilterStrategy {

    protected FilterWaitingSalesOrders(SalesOrderRepository salesOrderRepository) {
        super(salesOrderRepository);
    }

    @Override
    protected @Nullable Specification<SalesOrder> baseSpecType() {
        return SalesOrderSpecification.byWaitingStatus();
    }
}
