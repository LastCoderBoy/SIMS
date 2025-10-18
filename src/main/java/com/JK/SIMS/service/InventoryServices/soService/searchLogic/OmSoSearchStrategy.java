package com.JK.SIMS.service.InventoryServices.soService.searchLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class OmSoSearchStrategy implements SoStrategy{
    @Override
    public Page<SalesOrder> searchInSo(String text, int page, int size) {
        return null;
    }
}
