package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.repository.IC_repo.OutgoingStock_repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class OutgoingStockService {
    private static final Logger logger = LoggerFactory.getLogger(OutgoingStockService.class);
    private final OutgoingStock_repository outgoingStockRepository;

    public OutgoingStockService(OutgoingStock_repository outgoingStockRepository) {
        this.outgoingStockRepository = outgoingStockRepository;
    }

    public int totalOutgoingStockQuantity(){
        try{
            int totalQuantity = outgoingStockRepository.findAll().size();
            logger.info("OS (totalOutgoingStockQuantity): Total quantity: {}", totalQuantity);
            return totalQuantity;
        }
        catch (DataAccessException ex){
            throw new DatabaseException("OS (totalIncomingStockQuantity): Failed to retrieve total incoming stock quantity due to database error", ex);
        }
        catch (Exception e){
            throw new ServiceException("OS (totalOutgoingStockQuantity): Unexpected error occurred while retrieving total outgoing stock quantity", e);
        }
    }
}
