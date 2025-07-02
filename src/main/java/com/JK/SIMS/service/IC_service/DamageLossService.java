package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.damage_loss.*;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.UM_service.JWTService;
import jakarta.transaction.Transactional;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.JK.SIMS.service.GlobalServiceHelper.now;

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


    public DamageLossPageResponse getDamageLossDashboardData(int page, int size) {
        try {
            DamageLossMetrics damageLossMetrics = damageLoss_repository.getDamageLossMetrics();

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

    private PaginatedResponse<DamageLossDTO> getDamageLossData(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
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

    private void validateStockInput(InventoryData inventoryProduct, Integer lostQuantity){
        if(inventoryProduct.getCurrentStock() < lostQuantity){
            if(lostQuantity <= 0){
                throw new IllegalArgumentException("Lost level cannot be zero or negative.");
            }
            throw new IllegalArgumentException("Lost level must be lower than Stock Level.");
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
    public ApiResponse updateDamageLossProduct(Integer id, DamageLossDTORequest request) throws BadRequestException {
        try {
            DamageLoss report = damageLoss_repository.findById(id)
                    .orElseThrow(() -> new BadRequestException("DL (updateDamageLossProduct): Provided report ID is not found."));

            if (request == null) {
                throw new IllegalArgumentException("DL (updateDamageLossProduct): At least one field required to update.");
            }

            InventoryData currentProduct = report.getIcProduct();

            if (request.sku() != null) {
                returnBackStockLevel(currentProduct, report.getQuantityLost());
                InventoryServiceHelper.updateInventoryStatus(currentProduct);

                InventoryData newProduct = getInventoryProduct(request.sku());
                validateStockInput(newProduct, report.getQuantityLost());

                int remainingStock = newProduct.getCurrentStock() - report.getQuantityLost();
                newProduct.setCurrentStock(remainingStock);
                InventoryServiceHelper.updateInventoryStatus(newProduct);

                ic_repository.save(newProduct);
                report.setIcProduct(newProduct);
            }

            if (request.lossDate() != null) {
                if (request.lossDate().isAfter(LocalDateTime.now(clock))) {
                    throw new ValidationException("DL (updateDamageLossProduct): Loss date cannot be in the future");
                }
                report.setLossDate(request.lossDate());
            }

            if (request.quantityLost() != null) {
                int newQuantity = request.quantityLost();
                validateStockInput(currentProduct, newQuantity);
                int currentLostQuantity = report.getQuantityLost();

                int stockAdjustment = currentLostQuantity - newQuantity;
                int newStock = currentProduct.getCurrentStock() + stockAdjustment;
                currentProduct.setCurrentStock(newStock);
                InventoryServiceHelper.updateInventoryStatus(currentProduct);

                ic_repository.save(currentProduct);
                report.setQuantityLost(newQuantity);
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
        } catch (ValidationException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Failed to update damage/loss report due to internal error", e);
        }
    }

    private void returnBackStockLevel(InventoryData previousProduct, Integer previousReportQuantity){
        int totalStockAfterReturn = previousProduct.getCurrentStock() + previousReportQuantity;
        previousProduct.setCurrentStock(totalStockAfterReturn);
        ic_repository.save(previousProduct);
    }
}
