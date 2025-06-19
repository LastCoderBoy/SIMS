package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.repository.IC_repo.IncomingStock_repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class IncomingStockService {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockService.class);

    private final IncomingStock_repository incomingStock_repository;
    @Autowired
    public IncomingStockService(IncomingStock_repository incomingStockRepository) {
        this.incomingStock_repository = incomingStockRepository;
    }

    public int totalIncomingStockQuantity(){
        try{
            int totalQuantity = incomingStock_repository.findAll().size();
            logger.info("IS (totalIncomingStockQuantity): Total quantity: {}", totalQuantity);
            return totalQuantity;
        }
        catch (DataAccessException ex){
            throw new DatabaseException("IS (totalIncomingStockQuantity): Failed to retrieve total incoming stock quantity due to database error", ex);
        }
        catch (Exception e){
            throw new ServiceException("IS (totalIncomingStockQuantity): Unexpected error occurred while retrieving total incoming stock quantity", e);
        }
    }
}
