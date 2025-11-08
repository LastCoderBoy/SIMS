package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.reportAnalytics.InventoryHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryHealthServiceImpl implements InventoryHealthService {
    private final IC_repository icRepository;

    @Override
    @Transactional(readOnly = true)
    public InventoryReportMetrics getInventoryHealth () {
        try {
            return icRepository.getInventoryReportMetrics();
        } catch (DataAccessException e) {
            log.error("RA (getInventoryHealth): Database error: {}", e.getMessage());
            throw new DatabaseException("Failed to calculate inventory stock value", e);
        } catch (Exception e) {
            log.error("RA (getInventoryHealth): Unexpected error: {}", e.getMessage());
            throw new ServiceException("Internal Service Error occurred", e);
        }
    }
}
