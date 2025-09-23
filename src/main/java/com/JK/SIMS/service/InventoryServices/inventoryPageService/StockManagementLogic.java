package com.JK.SIMS.service.InventoryServices.inventoryPageService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.InsufficientStockException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockManagementLogic {

    private static final Logger logger = LoggerFactory.getLogger(StockManagementLogic.class);

    private final InventoryServiceHelper inventoryServiceHelper;
    private final IC_repository icRepository;
    @Autowired
    public StockManagementLogic(InventoryServiceHelper inventoryServiceHelper, IC_repository icRepository) {
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.icRepository = icRepository;
    }


    // Reserve stock atomically - returns true if successful, false if insufficient stock
    @Transactional
    public boolean reserveStock(String productId, Integer requestQuantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            // Validate the requested requestQuantity
            int availableStock = getAvailableStock(inventory);
            if (availableStock >= requestQuantity) {
                inventory.setReservedStock(inventory.getReservedStock() + requestQuantity);
                icRepository.save(inventory);
                logger.debug("IC (reserveStock): Reserved {} units for product {}", requestQuantity, productId);
                return true;
            }

            logger.warn("IC (reserveStock): Insufficient stock for product {}. Available: {}, Requested: {}",
                    productId, availableStock, requestQuantity);
            return false;

        } catch (DataAccessException e) {
            logger.error("IC (reserveStock): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to reserve stock", e);
        }
    }

    @Transactional
    public void fulfillReservation(String productId, int approvedQuantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            if(approvedQuantity > inventory.getReservedStock()){
                throw new InsufficientStockException("IC (fulfillReservation): Insufficient stock for product " + productId);
            }

            // Deduct from both current stock and reserved stock
            inventory.setCurrentStock(inventory.getCurrentStock() - approvedQuantity);
            inventory.setReservedStock(inventory.getReservedStock() - approvedQuantity);

            // Update status based on the new stock level
            inventoryServiceHelper.updateInventoryStatus(inventory);
            icRepository.save(inventory);

            logger.debug("IC (fulfillReservation): Fulfilled reservation of {} units for product {}", approvedQuantity, productId);
        } catch (DataAccessException e) {
            logger.error("IC (fulfillReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to fulfill reservation", e);
        }
    }

    // Release reservation when the order is cancelled
    @Transactional
    public void releaseReservation(String productId, int quantity) {
        try {
            InventoryData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - quantity));

            icRepository.save(inventory);
            logger.debug("IC (releaseReservation): Released reservation of {} units for product {}", quantity, productId);

        } catch (DataAccessException e) {
            logger.error("IC (releaseReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to release reservation", e);
        }
    }

    // Get available stock (current - reserved)
    private int getAvailableStock(InventoryData inventory) {
        return inventory.getCurrentStock() - inventory.getReservedStock();
    }
}
