package com.JK.SIMS.service.InventoryServices.inventoryPageService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.InsufficientStockException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Slf4j
public class StockManagementLogic {

    private final InventoryServiceHelper inventoryServiceHelper;
    private final IC_repository icRepository;
    @Autowired
    public StockManagementLogic(InventoryServiceHelper inventoryServiceHelper, IC_repository icRepository) {
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.icRepository = icRepository;
    }


    // Reserve stock atomically - throws exception if insufficient stock
    @Transactional
    public void reserveStock(String productId, Integer requestQuantity) {
        try {
            InventoryControlData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            // Validate the requested Quantity
            int availableStock = getAvailableStock(inventory);

            if (availableStock < requestQuantity) {
                log.warn("StockManagement reserveStock(): Insufficient stock for product {}. Available: {}, Requested: {}",
                        productId, availableStock, requestQuantity);
                throw new InsufficientStockException(
                        String.format("Insufficient stock for product %s.", productId));
            }
            inventory.setReservedStock(inventory.getReservedStock() + requestQuantity);
            icRepository.save(inventory);
            log.info("StockManagement reserveStock(): Reserved {} units for product {}", requestQuantity, productId);
        } catch (DataAccessException e) {
            log.error("StockManagement reserveStock(): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to reserve stock", e);
        } catch ( InsufficientStockException e){
            log.error("StockManagement reserveStock(): Insufficient stock - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("StockManagement reserveStock(): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Failed to reserve stock", e);
        }
    }

    @Transactional
    public void fulfillReservation(String productId, int approvedQuantity) {
        try {
            InventoryControlData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            if(approvedQuantity > inventory.getReservedStock()){
                throw new InsufficientStockException("IC (fulfillReservation): Approving more quantity than reserved for product " + productId);
            }

            // Deduct from both current stock and reserved stock
            inventory.setCurrentStock(inventory.getCurrentStock() - approvedQuantity);
            inventory.setReservedStock(inventory.getReservedStock() - approvedQuantity);

            // Update status based on the new stock level
            inventoryServiceHelper.updateInventoryStatus(inventory);
            icRepository.save(inventory);
            log.info("IC (fulfillReservation): Fulfilled reservation of {} units for product {}", approvedQuantity, productId);
        } catch (DataAccessException e) {
            log.error("IC (fulfillReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to fulfill reservation", e);
        } catch (Exception e) {
            log.error("IC (fulfillReservation): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Failed to fulfill reservation", e);
        }
    }

    // Release reservation when the order is cancelled or creation failed.
    @Transactional
    public void releaseReservation(String productId, int releasedQuantity) {
        try {
            InventoryControlData inventory = icRepository.findByProductIdWithLock(productId);
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory not found for product: " + productId);
            }

            if (inventory.getReservedStock() < releasedQuantity) {
                log.warn("Attempting to release {} but only {} reserved for product {}",
                        releasedQuantity, inventory.getReservedStock(), productId);
            }
            inventory.setReservedStock(Math.max(0, inventory.getReservedStock() - releasedQuantity));
            icRepository.save(inventory);
            log.debug("IC (releaseReservation): Released reservation of {} units for product {}", releasedQuantity, productId);
        } catch (DataAccessException e) {
            log.error("IC (releaseReservation): Database error - {}", e.getMessage());
            throw new DatabaseException("Failed to release reservation", e);
        } catch (Exception e) {
            log.error("IC (releaseReservation): Unexpected error - {}", e.getMessage());
            throw new ServiceException("Failed to release reservation", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateInventoryStockLevels(InventoryControlData existingProduct, Optional<Integer> newStockLevel, Optional<Integer> newMinLevel ) {
        // Update current stock if provided
        newStockLevel.ifPresent(existingProduct::setCurrentStock);

        // Update minimum level if provided
        newMinLevel.ifPresent(existingProduct::setMinLevel);

        //Update the status based on the latest update
        inventoryServiceHelper.updateInventoryStatus(existingProduct);

        icRepository.save(existingProduct);
    }

    // Get available stock (current - reserved)
    private int getAvailableStock(InventoryControlData inventory) {
        return inventory.getCurrentStock() - inventory.getReservedStock();
    }
}
