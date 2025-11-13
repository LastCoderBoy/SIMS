package com.JK.SIMS.service.salesOrder.salesOrderQueryService;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Shared query service for product-related read operations
 * Purpose: Break circular dependencies between SalesOrderService and other services
 * Contains ONLY read operations - no business logic or state changes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderQueryService {

    private final SalesOrderRepository salesOrderRepository;

    /**
     * Count active orders for product
     */
    @Transactional(readOnly = true)
    public long countActiveOrdersForProduct(String productId) {
        return salesOrderRepository.countActiveOrdersForProduct(productId);
    }

    /**
     * Get detailed info about active orders
     */
    @Transactional(readOnly = true)
    public List<String> getActiveOrderReferencesForProduct(String productId) {
        return salesOrderRepository.findActiveOrdersForProduct(productId)
                .stream()
                .map(SalesOrder::getOrderReference)
                .toList();
    }
}
