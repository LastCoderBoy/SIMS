package com.JK.SIMS.service.utilities.salesOrderSearchLogic;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import org.springframework.data.domain.Page;

public interface SoSearchStrategy {
    Page<SalesOrder> searchInSo(String text, int page, int size, String sortBy, String sortDirection);
}
