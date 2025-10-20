package com.JK.SIMS.service.utilities.salesOrderFilterLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import com.JK.SIMS.service.utilities.salesOrderFilterLogic.filterSpecification.SalesOrderSpecification;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class FilterWaitingSalesOrders extends AbstractSoFilterStrategy {

    protected FilterWaitingSalesOrders(SalesOrderRepository salesOrderRepository) {
        super(salesOrderRepository);
    }

    @Override
    protected @Nullable Specification<SalesOrder> baseSpecType() {
        return SalesOrderSpecification.byWaitingStatus();
    }
}
