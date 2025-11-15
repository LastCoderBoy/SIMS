package com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderSearchLogic;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component("icSoSearchStrategy")  // Must match field name
@Slf4j
public class IcSoSearchStrategy implements SoSearchStrategy {

    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public IcSoSearchStrategy(GlobalServiceHelper globalServiceHelper, SalesOrderRepository salesOrderRepository) {
        this.globalServiceHelper = globalServiceHelper;
        this.salesOrderRepository = salesOrderRepository;
    }

    // Search by Customer Name or Order Reference ID
    @Override
    public Page<SalesOrder> searchInSo(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            log.info("IcSo (searchInSo): Search text provided. Searching for orders with text '{}'", text);
            return salesOrderRepository.searchInWaitingSalesOrders(text, pageable);
        } catch (DataAccessException dae) {
            log.error("IcSo (searchInSo): Database error while searching orders", dae);
            throw new DatabaseException("IcSo (searchInSo): Error occurred while searching orders");
        } catch (Exception e) {
            log.error("IcSo (searchInSo): Unexpected error while searching orders", e);
            throw new ServiceException("IcSo (searchInSo): Error occurred while searching orders");
        }
    }
}
