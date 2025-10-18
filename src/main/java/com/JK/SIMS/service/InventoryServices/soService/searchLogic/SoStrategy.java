package com.JK.SIMS.service.InventoryServices.soService.searchLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import org.springframework.data.domain.Page;

public interface SoStrategy {
    Page<SalesOrder> searchInSo(String text, int page, int size);
}
