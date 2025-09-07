package com.JK.SIMS.service.salesOrderService;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SoServiceInIc {

    private static final Logger logger = LoggerFactory.getLogger(SoServiceInIc.class);

    private final SalesOrderServiceHelper salesOrderServiceHelper;
    private final SalesOrderRepository salesOrderRepository;
    @Autowired
    public SoServiceInIc(SalesOrderServiceHelper salesOrderServiceHelper, SalesOrderRepository salesOrderRepository) {
        this.salesOrderServiceHelper = salesOrderServiceHelper;
        this.salesOrderRepository = salesOrderRepository;
    }

    // Will be used in the SORT logic and the normal GET all logic.
    // Can be only sorted using Status.
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllWaitingSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            //TODO: Complete repository method.
            Page<SalesOrder> salesOrders = salesOrderRepository.findAllWaitingSalesOrders(pageable, sort);

            Page<SalesOrderResponseDto> dtoResponse = salesOrders.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);

        } catch (Exception e) {
            logger.error("OS (getAllSalesOrdersSorted): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

}
