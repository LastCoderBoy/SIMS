package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLoss;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DamageLossService {

    private static final Logger logger  = LoggerFactory.getLogger(DamageLossService.class);
    private final DamageLoss_repository damageLoss_repository;
    @Autowired
    public DamageLossService(DamageLoss_repository damageLoss_repository){
        this.damageLoss_repository = damageLoss_repository;
    }

    public int totalDamageLossQuantity(){
        try{
            int totalQuantity = damageLoss_repository.findAll().size();
            logger.info("DL (totalDamageLossQuantity): Total quantity: {}", totalQuantity);
            return totalQuantity;
        }
        catch (DataAccessException ex){
            throw new DatabaseException("DL (totalDamageLossQuantity): Failed due to database error", ex);
        }
        catch (Exception e){
            throw new ServiceException("DL (totalDamageLossQuantity): Unexpected error occurred", e);
        }
    }
}
