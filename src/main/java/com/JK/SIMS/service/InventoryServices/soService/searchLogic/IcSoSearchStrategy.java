package com.JK.SIMS.service.InventoryServices.soService.searchLogic;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.soService.SalesOrderServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IcSoSearchStrategy implements SoStrategy{

    private static final Logger logger = LoggerFactory.getLogger(IcSoSearchStrategy.class);

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderServiceHelper soServiceHelper;
    @Autowired
    public IcSoSearchStrategy(SalesOrderRepository salesOrderRepository, SalesOrderServiceHelper soServiceHelper) {
        this.salesOrderRepository = salesOrderRepository;
        this.soServiceHelper = soServiceHelper;
    }

    @Override
    public PaginatedResponse<SalesOrderResponseDto> searchInSo(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable(text);
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
                logger.info("IcSo (searchInSo): Search text provided. Searching for orders with text '{}'", text);
                Page<SalesOrder> entityResponse = salesOrderRepository.searchInOutgoingSalesOrders(text, pageable);
                Page<SalesOrderResponseDto> mappedEntity = entityResponse.map(soServiceHelper::convertToOrderResponseDto);
                return new PaginatedResponse<>(mappedEntity);
            }
            return new PaginatedResponse<>();
        } catch (DataAccessException dae) {
            logger.error("IcSo (searchInSo): Database error while searching orders", dae);
            throw new DatabaseException("IcSo (searchInSo): Error occurred while searching orders");
        }
        catch (Exception e) {
            logger.error("IcSo (searchInSo): Unexpected error while searching orders", e);
            throw new ServiceException("IcSo (searchInSo): Error occurred while searching orders");
        }
    }
}
