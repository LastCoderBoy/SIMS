package com.JK.SIMS.service.InventoryServices.damageLossService.damageLossQueryService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossMetrics;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossResponse;
import com.JK.SIMS.repository.damageLossRepo.DamageLossRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DamageLossQueryService {
    private final DamageLossRepository damageLossRepository;

    @Transactional(readOnly = true)
    public DamageLoss getDamageLossById(Integer id){
        return damageLossRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DamageLoss Report not found for ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<DamageLoss> getAllDamageLoss(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("icProduct.pmProduct.name").descending());
            return damageLossRepository.findAll(pageable);
        } catch (DataAccessException de) {
            log.error("DL (getAllDamageLoss): Database error occurred: {}", de.getMessage(), de);
            throw new DatabaseException("Internal Database error.", de);
        } catch (Exception e) {
            log.error("DL (getAllDamageLoss): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service error", e);
        }
    }

    @Transactional(readOnly = true)
    public DamageLossMetrics getDamageLossMetrics() {
        try {
            return damageLossRepository.getDamageLossMetrics();
        } catch (DataAccessException de) {
            log.error("DL (getDamageLossMetrics): Failed to retrieve damage/loss metrics", de);
            throw new DatabaseException("Failed to retrieve damage/loss metrics", de);
        } catch (Exception e) {
            log.error("DL (getDamageLossMetrics): Unexpected error retrieving damage/loss metrics", e);
            throw new ServiceException("Unexpected error retrieving damage/loss metrics", e);
        }
    }

    @Transactional(readOnly = true)
    public Long countTotalDamagedProducts() {
        try {
            return damageLossRepository.countTotalDamagedProducts();
        } catch (DataAccessException de) {
            log.error("DL (countTotalDamagedProducts): Failed to retrieve total damaged products", de);
            throw new DatabaseException("Failed to retrieve total damaged products", de);
        }
    }
}
