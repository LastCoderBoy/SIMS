package com.JK.SIMS.service.purchaseOrderFilterLogic;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.purchaseOrderFilterLogic.filterSpecification.PurchaseOrderSpecification;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class PendingPoFilterStrategy extends AbstractPoFilterStrategy {

    protected PendingPoFilterStrategy(PurchaseOrderServiceHelper poServiceHelper, PurchaseOrderRepository purchaseOrderRepository) {
        super(poServiceHelper, purchaseOrderRepository);
    }

    @Override
    protected @Nullable Specification<PurchaseOrder> baseSpecType() {
        return PurchaseOrderSpecification.isPending();
    }
}
