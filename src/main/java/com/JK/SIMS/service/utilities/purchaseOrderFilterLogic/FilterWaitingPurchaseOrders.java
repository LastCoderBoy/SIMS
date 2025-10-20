package com.JK.SIMS.service.utilities.purchaseOrderFilterLogic;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.utilities.purchaseOrderFilterLogic.filterSpecification.PurchaseOrderSpecification;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class FilterWaitingPurchaseOrders extends AbstractPoFilterStrategy {

    protected FilterWaitingPurchaseOrders(PurchaseOrderServiceHelper poServiceHelper, PurchaseOrderRepository purchaseOrderRepository) {
        super(poServiceHelper, purchaseOrderRepository);
    }

    @Override
    protected @Nullable Specification<PurchaseOrder> baseSpecType() {
        return PurchaseOrderSpecification.isPending();
    }
}
