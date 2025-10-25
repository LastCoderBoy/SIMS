package com.JK.SIMS.service.utilities.salesOrderFilterLogic;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class FilterSalesOrders extends AbstractSoFilterStrategy {
    protected FilterSalesOrders(SalesOrderRepository salesOrderRepository) {
        super(salesOrderRepository);
    }

    @Override
    protected @Nullable Specification<SalesOrder> baseSpecType() {
        return null;
    }
}
