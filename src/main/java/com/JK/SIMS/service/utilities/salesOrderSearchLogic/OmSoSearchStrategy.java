package com.JK.SIMS.service.utilities.salesOrderSearchLogic;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OmSoSearchStrategy implements SoSearchStrategy {

    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public OmSoSearchStrategy(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Override
    public Page<SalesOrder> searchInSo(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            log.info("OM-SO searchInSo(): Search text provided. Searching for orders with text '{}'", text);
            return salesOrderRepository.searchInSalesOrders(text, pageable);
        } catch (Exception e) {
            log.error("OM-SO searchInSo(): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders");
        }
    }
}
