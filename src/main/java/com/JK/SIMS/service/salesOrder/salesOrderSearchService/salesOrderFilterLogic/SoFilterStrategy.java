package com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface SoFilterStrategy {
    Page<SalesOrder> filterSalesOrders(SalesOrderStatus status, String optionDate,
                                       LocalDate startDate, LocalDate endDate, Pageable pageable);
}
