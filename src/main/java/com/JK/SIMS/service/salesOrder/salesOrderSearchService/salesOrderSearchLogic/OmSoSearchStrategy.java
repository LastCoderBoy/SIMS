package com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderSearchLogic;

import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component("omSoSearchStrategy")  // Must match field name
@Slf4j
public class OmSoSearchStrategy implements SoSearchStrategy {

    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public OmSoSearchStrategy(GlobalServiceHelper globalServiceHelper, SalesOrderRepository salesOrderRepository) {
        this.globalServiceHelper = globalServiceHelper;
        this.salesOrderRepository = salesOrderRepository;
    }

    @Override
    public Page<SalesOrder> searchInSo(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            log.info("OM-SO searchInSo(): Search text provided. Searching for orders with text '{}'", text);
            return salesOrderRepository.searchInSalesOrders(text, pageable);
        } catch (Exception e) {
            log.error("OM-SO searchInSo(): Error filtering orders - {}", e.getMessage());
            throw new ServiceException("Failed to filter orders");
        }
    }
}
