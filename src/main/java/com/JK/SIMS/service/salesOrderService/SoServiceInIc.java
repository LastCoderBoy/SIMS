package com.JK.SIMS.service.salesOrderService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public PaginatedResponse<SalesOrderResponseDto> getAllWaitingSalesOrders(@Min(0) int page,
                                                                             @Min(1) @Max(100) int size,
                                                                             String sortBy, String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SalesOrder> salesOrders = salesOrderRepository.findAllWaitingSalesOrders(pageable);

            Page<SalesOrderResponseDto> dtoResponse = salesOrders.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);

        } catch (Exception e) {
            logger.error("OS (getAllSalesOrdersSorted): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    // Urgent Shipment table (CurrentDate + 2 > estimatedDeliveryDate)
    @Transactional(readOnly = true)
    public PaginatedResponse<SalesOrderResponseDto> getAllUrgentSalesOrders(@Min(0) int page, @Min(1) @Max(100) int size, String sortBy, String sortDir) {
        try {
            // Create the Pageable object with Sort.
            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            // Get the page of urgent orders.
            Page<SalesOrder> entityResponse = salesOrderRepository.findAllUrgentSalesOrders(pageable);
            Page<SalesOrderResponseDto> dtoResponse = entityResponse.map(salesOrderServiceHelper::convertToOrderResponseDto);
            return new PaginatedResponse<>(dtoResponse);
        } catch (DataAccessException da){
            logger.error("OS (getAllUrgentSalesOrders): Failed to retrieve orders due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve orders due to database error", da);
        } catch (Exception e) {
            logger.error("OS (getAllUrgentSalesOrders): Error fetching orders - {}", e.getMessage());
            throw new ServiceException("Failed to fetch orders", e);
        }
    }

    // Average Fulfill Time.
    // Method for future reference.
    @Transactional(readOnly = true)
    public int getAverageFulfillTime() {
        try {
            long totalEntities = salesOrderRepository.count();
            if(totalEntities == 0) return 0;
            long totalDeliveryDate = salesOrderRepository.calculateTotalDeliveryDate();
            return (int) (totalDeliveryDate / totalEntities);
        } catch (DataAccessException da) {
            logger.error("OS (getAverageFulfillTime): Failed to calculate average fulfill time: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to calculate average fulfill time", da);
        } catch (Exception e) {
            logger.error("OS (getAverageFulfillTime): Error calculating average fulfill time: {}", e.getMessage());
            throw new ServiceException("Failed to calculate average fulfill time", e);
        }
    }
}
