package com.JK.SIMS.service.IC_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.damage_loss.*;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.DamageLoss_repository;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.UM_service.JWTService;
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
                damageLoss.getIcProduct().getPmProduct().getName(),
                damageLoss.getIcProduct().getPmProduct().getCategory(),
                damageLoss.getIcProduct().getSKU(),
                damageLoss.getQuantityLost(),
                damageLoss.getLossValue(),
                damageLoss.getReason(),
                damageLoss.getLossDate());
    }

    public void addDamageLoss(DamageLossDTORequest dtoRequest, String jwtToken) {
        try{
            InventoryServiceHelper.validateDamageLossDTOInput(dtoRequest);

            InventoryData inventoryProduct = getInventoryProduct(String.valueOf(dtoRequest.sku()));
            if(inventoryProduct.getCurrentStock() < dtoRequest.quantityLost()){
                if(dtoRequest.quantityLost() <= 0){
                    throw new IllegalArgumentException("Lost level cannot be zero or negative.");
                }
                throw new IllegalArgumentException("Lost level must be lower than Stock Level.");
            }

            String username = jWTService.extractUsername(jwtToken);
            DamageLoss entity = convertToEntity(dtoRequest, inventoryProduct, username);
            damageLoss_repository.save(entity);

            // Subtract the Lost Quantity from the Stock Level
            int remainingStock = inventoryProduct.getCurrentStock() - dtoRequest.quantityLost();
            inventoryProduct.setCurrentStock(remainingStock);

            if(inventoryProduct.getStatus() == InventoryDataStatus.IN_STOCK &&
                inventoryProduct.getCurrentStock() <= inventoryProduct.getMinLevel()){
                inventoryProduct.setStatus(InventoryDataStatus.LOW_STOCK);
            }

            ic_repository.save(inventoryProduct);
        } catch (DataAccessException de){
            throw new DatabaseException("DL (addDamageLoss): Database error.", de);
        } catch (ValidationException | IllegalArgumentException ex){
            throw new ValidationException(ex.getMessage());
        } catch (Exception e){
            throw new ServiceException("DL (addDamageLoss): Internal Service error", e);
        }
    }

    private DamageLoss convertToEntity(DamageLossDTORequest dto, InventoryData inventoryData, String user){
        DamageLoss entity = new DamageLoss();

        BigDecimal price = inventoryData.getPmProduct().getPrice();
        BigDecimal lossValue = price.multiply(new BigDecimal(dto.quantityLost()));

        entity.setIcProduct(inventoryData);
        entity.setQuantityLost(dto.quantityLost());
        entity.setLossValue(lossValue);
        entity.setCreatedAt(now(clock));
        entity.setReason(dto.reason());
        entity.setRecordedBy(user);
        entity.setUpdatedAt(now(clock));
        entity.setLossDate(dto.lossDate() != null ? dto.lossDate() : now(clock));
        return entity;
    }

    private InventoryData getInventoryProduct(String sku){
        return ic_repository.findBySKU(sku)
                .orElseThrow(() -> new IllegalArgumentException("Invalid SKU: " + sku));
    }

}
