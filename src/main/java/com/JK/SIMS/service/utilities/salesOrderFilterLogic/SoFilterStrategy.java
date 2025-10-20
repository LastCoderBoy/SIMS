package com.JK.SIMS.service.utilities.salesOrderFilterLogic;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface SoFilterStrategy {
    Page<SalesOrder> filterSalesOrders(SalesOrderStatus status, String optionDate,
                                       LocalDate startDate, LocalDate endDate, Pageable pageable);
}
