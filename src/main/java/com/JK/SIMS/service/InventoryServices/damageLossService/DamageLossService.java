package com.JK.SIMS.service.InventoryServices.damageLossService;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.LossReason;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossMetrics;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossPageResponse;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossRequest;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.damageLossRepo.DamageLossRepository;
import com.JK.SIMS.service.InventoryServices.damageLossService.damageLossQueryService.DamageLossQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryDashboardService.stockManagement.StockManagementLogic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class DamageLossService {
    private final Clock clock;

    // =========== Components ===========
    private final SecurityUtils securityUtils;
    private final StockManagementLogic stockManagementLogic;
    private final DamageLossQueryService damageLossQueryService;
    private final InventoryQueryService inventoryQueryService;

    // =========== Helpers & Utilities ===========
    private final DamageLossHelper damageLossHelper;

    // =========== Repositories ===========
    private final DamageLossRepository damageLossRepository;

    @Transactional(readOnly = true)
    public DamageLossPageResponse getDamageLossDashboardData(int page, int size) {
        DamageLossMetrics damageLossMetrics = damageLossQueryService.getDamageLossMetrics();
        Page<DamageLoss> pagedDamageLossEntity = damageLossQueryService.getAllDamageLoss(page, size);

        return new DamageLossPageResponse(
                damageLossMetrics.getTotalReport(),
                damageLossMetrics.getTotalItemLost(),
                damageLossMetrics.getTotalLossValue(),
                damageLossHelper.transformToPaginatedDTO(pagedDamageLossEntity)
        );
    }

    @Transactional
    public void addDamageLoss(DamageLossRequest dtoRequest, String jwtToken) {
        try {
            damageLossHelper.validateDamageLossDto(dtoRequest);

            InventoryControlData inventoryProduct =
                    inventoryQueryService.getInventoryDataBySku(dtoRequest.sku());
            damageLossHelper.validateStockInput(inventoryProduct, dtoRequest.quantityLost());

            String username = securityUtils.validateAndExtractUsername(jwtToken);
            DamageLoss entity = damageLossHelper.convertToEntity(dtoRequest, inventoryProduct, username);
            damageLossRepository.save(entity);

            // Update the Inventory Stock level and the status
            int remainingStock = inventoryProduct.getCurrentStock() - dtoRequest.quantityLost();
            stockManagementLogic.updateInventoryStockLevels(
                    inventoryProduct,
                    Optional.of(remainingStock),
                    Optional.empty()
            );
        } catch (DataAccessException de) {
            log.error("DL (addDamageLoss): Database error while saving damage/loss record: {}", de.getMessage());
            throw new DatabaseException("Database error while saving damage/loss record", de);
        } catch (IllegalArgumentException ie) {
            throw new ValidationException("DL (addDamageLoss): " + ie.getMessage());
        } catch (Exception e) {
            throw new ServiceException("DL (addDamageLoss): Internal service error while processing damage/loss record", e);
        }
    }

    @Transactional
    public ApiResponse<DamageLoss> updateDamageLossProduct(Integer id, DamageLossRequest request) {
        try {
            DamageLoss report = damageLossQueryService.getDamageLossById(id);
            if (request == null || damageLossHelper.isRequestEmpty(request)) {
                throw new ValidationException("DL (updateDamageLossProduct): At least one field required to update.");
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
                InventoryControlData inventoryProduct = report.getIcProduct();

                int newQuantity = request.quantityLost();
                damageLossHelper.validateStockInput(inventoryProduct, newQuantity);
                int currentLostQuantity = report.getQuantityLost();

                // Update the Inventory Stock level
                int stockAdjustment = currentLostQuantity - newQuantity;
                restoreStockLevel(inventoryProduct, stockAdjustment);

                // Set the new Lost Quantity
                report.setQuantityLost(newQuantity);

                // Update the Loss Value based on the new Quantity
                BigDecimal price = inventoryProduct.getPmProduct().getPrice();
                BigDecimal updatedLossValue = price.multiply(BigDecimal.valueOf(newQuantity));
                report.setLossValue(updatedLossValue);
            }

            if (request.reason() != null) {
                report.setReason(request.reason());
            }

            damageLossRepository.save(report);

            log.info("DL (updateDamageLossProduct): Successfully updated damage/loss report for SKU: {}",
                    report.getIcProduct().getSKU());
            return new ApiResponse<>(true, report.getIcProduct().getSKU() + " SKU is updated successfully.");

        } catch (DataAccessException e) {
            throw new DatabaseException("Failed to update damage/loss report due to database error", e);
        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Failed to update damage/loss report due to internal error", e);
        }
    }

    // NOTE: Deleting Report will return the Inventory Level into previous state
    @Transactional
    public ApiResponse<String> deleteDamageLossReport(Integer id) {
        try {
            DamageLoss report = damageLossQueryService.getDamageLossById(id);
            restoreStockLevel(report.getIcProduct(), report.getQuantityLost());
            damageLossRepository.delete(report);

            log.info("DL (delete): Deleted damage/loss report and restored inventory for SKU {}", report.getIcProduct().getSKU());
            return new ApiResponse<>(true, "Report deleted and stock restored for SKU: " + report.getIcProduct().getSKU());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("DL (delete): Error while deleting report: {}", e.getMessage(), e);
            throw new ServiceException("Error while deleting report", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<DamageLossResponse> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("icProduct.pmProduct.name").ascending());
                Page<DamageLoss> damageLossReports =
                        damageLossRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                log.info("DL (searchProduct): {} products retrieved.", damageLossReports.getContent().size());
                return damageLossHelper.transformToPaginatedDTO(damageLossReports);
            }

            log.info("DL (searchProduct): No search text provided. Retrieving first page with default size.");
            Page<DamageLoss> pagedDamageLossEntity = damageLossQueryService.getAllDamageLoss(page, size);
            return damageLossHelper.transformToPaginatedDTO(pagedDamageLossEntity);

        } catch (DataAccessException e) {
            throw new DatabaseException("DL (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("DL (searchProduct): Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<DamageLossResponse> filterProducts(String reason, String sortBy, String sortDirection, int page, int size) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            reason = reason.trim().toUpperCase();
            LossReason lossReason = LossReason.valueOf(reason);
            Page<DamageLoss> foundReports = damageLossRepository.findByReason(lossReason, pageable);
            log.info("DL (filterProducts): {} products retrieved.", foundReports.getContent().size());
            return damageLossHelper.transformToPaginatedDTO(foundReports);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    private void restoreStockLevel(InventoryControlData product, int quantityToRestore) {
        if (quantityToRestore <= 0) {
            log.warn("DL (restoreStockLevel): Quantity to restore is zero or negative. Skipping update.");
            return;
        }

        int updatedStock = product.getCurrentStock() + quantityToRestore;
        stockManagementLogic.updateInventoryStockLevels(product, Optional.of(updatedStock), Optional.empty());

        log.info("DL (restoreStockLevel): Restored {} units to SKU {}. New stock: {}",
                quantityToRestore, product.getSKU(), updatedStock);
    }
}
