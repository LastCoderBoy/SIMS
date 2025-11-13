package com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderFilterLogic;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component("filterPurchaseOrders")
public class FilterPurchaseOrders extends AbstractPoFilterStrategy {

    protected FilterPurchaseOrders(PurchaseOrderServiceHelper poServiceHelper, PurchaseOrderRepository purchaseOrderRepository) {
        super(poServiceHelper, purchaseOrderRepository);
    }

    @Override
    protected @Nullable Specification<PurchaseOrder> baseSpecType() {
        return null; // No specification needed for this filter strategy
    }
}
