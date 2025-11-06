package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.reportAnalytics.InventoryHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryHealthServiceImpl implements InventoryHealthService {
    private final IC_repository icRepository;

    @Override
    @Transactional(readOnly = true)
    public Long countStockQuantity(){

    }

    @Override
    @Transactional(readOnly = true)
    public Long countReservedStockQuantity(){

    }

    @Override
    @Transactional(readOnly = true)
    public Long countOutOfStockQuantity(){

    }

    @Override
    @Transactional(readOnly = true)
    public Long countLowStockQuantity(){

    }

    // available in the Stock and higher than the Min_Level
    @Override
    @Transactional(readOnly = true)
    public Long countInStockQuantity(){

    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getInventoryStockValue() {
        try {
            BigDecimal value = icRepository.getInventoryStockValue();
            return value != null ? value : BigDecimal.ZERO;
        } catch (DataAccessException e) {
            log.error("IC (getInventoryStockValue): Database error: {}", e.getMessage());
            throw new DatabaseException("Failed to calculate inventory stock value", e);
        } catch (Exception e) {
            log.error("IC (getInventoryStockValue): Unexpected error: {}", e.getMessage());
            throw new ServiceException("Internal Service Error occurred", e);
        }
    }
}
