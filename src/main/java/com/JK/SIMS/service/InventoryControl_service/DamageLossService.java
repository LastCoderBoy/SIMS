package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.damage_loss.*;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.userManagement_service.JWTService;
import org.apache.coyote.BadRequestException;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DamageLossService {

    private static final Logger logger  = LoggerFactory.getLogger(DamageLossService.class);
    private final DamageLoss_repository damageLoss_repository;
    private final JWTService jWTService;
    private final IC_repository ic_repository;
    private final Clock clock;

    @Autowired
    public DamageLossService(DamageLoss_repository damageLoss_repository, JWTService jWTService, IC_repository iC_repository, Clock clock){
        this.damageLoss_repository = damageLoss_repository;
        this.jWTService = jWTService;
        this.ic_repository = iC_repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DamageLossPageResponse getDamageLossDashboardData(int page, int size) {
        try {
            DamageLossMetrics damageLossMetrics = getDamageLossMetrics();

            return new DamageLossPageResponse(
                    damageLossMetrics.getTotalReport(),
                    damageLossMetrics.getTotalItemLost(),
                    damageLossMetrics.getTotalLossValue(),
                    getDamageLossData(page, size)
            );
        } catch (DataAccessException de) {
            throw new DatabaseException("DL (getDamageLossDashboardData): Database error.", de);
        } catch (Exception e) {
            throw new ServiceException("DL (getDamageLossDashboardData): Internal Service error", e);
        }
    }

    @Transactional
    public void addDamageLoss(DamageLossDTORequest dtoRequest, String jwtToken) {
        try {
            InventoryServiceHelper.nullCheckValidation(dtoRequest);

            InventoryData inventoryProduct = getInventoryProduct(dtoRequest.sku());
            validateStockInput(inventoryProduct, dtoRequest.quantityLost());

            String username = jWTService.extractUsername(jwtToken);
            DamageLoss entity = convertToEntity(dtoRequest, inventoryProduct, username);
            damageLoss_repository.save(entity);

            int remainingStock = inventoryProduct.getCurrentStock() - dtoRequest.quantityLost();
            inventoryProduct.setCurrentStock(remainingStock);
            InventoryServiceHelper.updateInventoryStatus(inventoryProduct);
            ic_repository.save(inventoryProduct);
        } catch (DataAccessException de) {
            throw new DatabaseException("DL (addDamageLoss): Database error while saving damage/loss record", de);
        } catch (IllegalArgumentException ie) {
            throw new ValidationException("DL (addDamageLoss): " + ie.getMessage());
        } catch (Exception e) {
            throw new ServiceException("DL (addDamageLoss): Internal service error while processing damage/loss record", e);
        }
    }

    // Helper method for internal use in services
    @Transactional(readOnly = true)
    public DamageLossMetrics getDamageLossMetrics() {
        try {
            return damageLoss_repository.getDamageLossMetrics();
        } catch (DataAccessException de) {
            throw new DatabaseException("DL (getDamageLossMetrics): Failed to retrieve damage/loss metrics", de);
        } catch (Exception e) {
            throw new ServiceException("DL (getDamageLossMetrics): Unexpected error retrieving damage/loss metrics", e);
        }
    }

    private void validateStockInput(InventoryData inventoryProduct, Integer lostQuantity){
        if(inventoryProduct.getCurrentStock() < lostQuantity){
            if(lostQuantity <= 0){
                throw new IllegalArgumentException("Lost level cannot be zero or negative.");
            }
            throw new IllegalArgumentException("Lost level must be lower than or equal to Stock Level.");
        }
    }

    private DamageLoss convertToEntity(DamageLossDTORequest dto, InventoryData inventoryData, String user) {
        BigDecimal price = inventoryData.getPmProduct().getPrice();
        BigDecimal lossValue = price.multiply(BigDecimal.valueOf(dto.quantityLost()));

        return new DamageLoss(
                null,
                inventoryData,
                dto.quantityLost(),
                dto.reason(),
                lossValue,
                dto.lossDate() != null ? dto.lossDate() : LocalDateTime.now(clock),
                user,
                LocalDateTime.now(clock),
                LocalDateTime.now(clock)
        );
    }

    private InventoryData getInventoryProduct(String sku){
        return ic_repository.findBySKU(sku)
                .orElseThrow(() -> new IllegalArgumentException("DL (getInventoryProduct): Invalid SKU: " + sku));
    }

    @Transactional
    public ApiResponse deleteDamageLossReport(Integer id) {
        try {
            DamageLoss report = damageLoss_repository.findById(id)
                    .orElseThrow(() -> new BadRequestException("DL (delete): Report not found for ID: " + id));

            restoreStockLevel(report.getIcProduct(), report.getQuantityLost());
            damageLoss_repository.delete(report);

            logger.info("DL (delete): Deleted damage/loss report and restored inventory for SKU {}", report.getIcProduct().getSKU());
            return new ApiResponse(true, "Report deleted and stock restored for SKU: " + report.getIcProduct().getSKU());
        } catch (Exception e) {
            throw new ServiceException("DL (delete): Error while deleting report", e);
        }
    }

    public PaginatedResponse<DamageLossDTO> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("icProduct.pmProduct.name").ascending());
                Page<DamageLoss> damageLossReports = damageLoss_repository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                logger.info("DL (searchProduct): {} products retrieved.", damageLossReports.getContent().size());
                return transformToPaginatedDTO(damageLossReports);
            }
            logger.info("DL (searchProduct): No search text provided. Retrieving first page with default size.");
            return getDamageLossData(page,size);
        } catch (DataAccessException e) {
            throw new DatabaseException("DL (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("DL (searchProduct): Failed to retrieve products", e);
        }
    }

    public PaginatedResponse<DamageLossDTO> filterProducts(String reason, String sortBy, String sortDirection, int page, int size) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            reason = reason.trim().toUpperCase();
            LossReason lossReason = LossReason.valueOf(reason);
            Page<DamageLoss> foundReports = damageLoss_repository.findByReason(lossReason, pageable);
            logger.info("DL (filterProducts): {} products retrieved.", foundReports.getContent().size());
            return transformToPaginatedDTO(foundReports);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }


    @Transactional
    public ApiResponse updateDamageLossProduct(Integer id, DamageLossDTORequest request) throws BadRequestException {
        try {
            DamageLoss report = damageLoss_repository.findById(id)
                    .orElseThrow(() -> new BadRequestException("DL (updateDamageLossProduct): Provided report ID is not found."));

            if (request == null) {
                throw new IllegalArgumentException("DL (updateDamageLossProduct): At least one field required to update.");
            }

            // WE DON'T UPDATE THE SKU, if the record is wrong, delete and create a new one.
            if(request.sku() != null){
                throw new ValidationException("DL (update): Cannot update the SKU, please recreate a new report");
            }

            if (request.lossDate() != null) {
                if (request.lossDate().isAfter(LocalDateTime.now(clock))) {
                    throw new ValidationException("DL (updateDamageLossProduct): Loss date cannot be in the future");
                }
                report.setLossDate(request.lossDate());
            }

            if (request.quantityLost() != null) {
                InventoryData currentProduct = report.getIcProduct();
                int newQuantity = request.quantityLost();
                validateStockInput(currentProduct, newQuantity);
                int currentLostQuantity = report.getQuantityLost();

                // Update the Inventory Stock level
                int stockAdjustment = currentLostQuantity - newQuantity;
                restoreStockLevel(currentProduct, stockAdjustment);

                // Set the new Quantity
                report.setQuantityLost(newQuantity);

                // Update the Loss Value based on the new Quantity
                BigDecimal price = currentProduct.getPmProduct().getPrice();
                BigDecimal updatedLossValue = price.multiply(BigDecimal.valueOf(newQuantity));
                report.setLossValue(updatedLossValue);
            }

            if (request.reason() != null) {
                report.setReason(request.reason());
            }

            report.setUpdatedAt(LocalDateTime.now(clock));
            damageLoss_repository.save(report);

            logger.info("DL (updateDamageLossProduct): Successfully updated damage/loss report for SKU: {}",
                    report.getIcProduct().getSKU());
            return new ApiResponse(true, report.getIcProduct().getSKU() + " SKU is updated successfully.");

        } catch (DataAccessException e) {
            throw new DatabaseException("Failed to update damage/loss report due to database error", e);
        } catch (ValidationException | BadRequestException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Failed to update damage/loss report due to internal error", e);
        }
    }

    private void restoreStockLevel(InventoryData product, int quantityToRestore) {
        if (quantityToRestore <= 0) {
            logger.warn("DL (restoreStockLevel): Quantity to restore is zero or negative. Skipping update.");
            return;
        }

        int updatedStock = product.getCurrentStock() + quantityToRestore;
        product.setCurrentStock(updatedStock);

        InventoryServiceHelper.updateInventoryStatus(product);
        ic_repository.save(product);

        logger.info("DL (restoreStockLevel): Restored {} units to SKU {}. New stock: {}",
                quantityToRestore, product.getSKU(), updatedStock);
    }

    private PaginatedResponse<DamageLossDTO> getDamageLossData(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("icProduct.pmProduct.name").ascending());
            Page<DamageLoss> dbResponse = damageLoss_repository.findAll(pageable);

            PaginatedResponse<DamageLossDTO> dtoResult = transformToPaginatedDTO(dbResponse);
            logger.info("DL (getDamageLossData): Returning {} paginated data", dtoResult.getContent().size());
            return dtoResult;
        } catch (DataAccessException de) {
            throw new DatabaseException("DL (getDamageLossData): Database error.", de);
        } catch (Exception e) {
            throw new ServiceException("DL (getDamageLossData): Internal Service error", e);
        }
    }

    private PaginatedResponse<DamageLossDTO> transformToPaginatedDTO(Page<DamageLoss> dbResponse) {
        PaginatedResponse<DamageLossDTO> result = new PaginatedResponse<>();
        result.setContent(dbResponse.getContent().stream().map(this::convertToDTO).toList());
        result.setTotalElements(dbResponse.getTotalElements());
        result.setTotalPages(dbResponse.getTotalPages());
        return result;
    }

    private DamageLossDTO convertToDTO(DamageLoss damageLoss) {
        return new DamageLossDTO(
                damageLoss.getId(),
                damageLoss.getIcProduct().getPmProduct().getName(),
                damageLoss.getIcProduct().getPmProduct().getCategory(),
                damageLoss.getIcProduct().getSKU(),
                damageLoss.getQuantityLost(),
                damageLoss.getLossValue(),
                damageLoss.getReason(),
                damageLoss.getLossDate());
    }
}
