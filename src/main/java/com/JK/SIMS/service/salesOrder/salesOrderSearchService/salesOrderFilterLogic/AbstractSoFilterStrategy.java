package com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic;

import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic.filterSpecification.SalesOrderSpecification;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSoFilterStrategy implements SoFilterStrategy {
    protected final SalesOrderRepository salesOrderRepository;

    protected @Nullable
    abstract Specification<SalesOrder> baseSpecType();

    @Override
    @Transactional(readOnly = true)
    public Page<SalesOrder> filterSalesOrders(SalesOrderStatus status, String optionDate, LocalDate startDate,
                                              LocalDate endDate, Pageable pageable) {

        try {
            // Always filtered by the allowed statuses
            Specification<SalesOrder> specification = Specification.where(baseSpecType());

            // Filtering by status if provided
            if (status != null) {
                specification = specification.and(
                        SalesOrderSpecification.byStatus(status));
            }

            // Filtering by dates
            if (optionDate != null && !optionDate.isEmpty()) {
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("filterSalesOrders(): Start date and end date must be provided for date filtering.");
                }
                if (startDate.isAfter(endDate)) {
                    throw new IllegalArgumentException("filterSalesOrders():  Start date must be before or equal to end date.");
                }
                String option = optionDate.toLowerCase().trim();
                specification = specification.and(SalesOrderSpecification.byDatesBetween(option, startDate, endDate));
            }

            // Database call and conversion to DTO
            return salesOrderRepository.findAll(specification, pageable);
        } catch (IllegalArgumentException ie) {
            log.error("OM-SO filterSalesOrders(): Invalid parameters are provided: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            log.error("OM-SO filterSalesOrders(): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}
