package com.JK.SIMS.service.InventoryServices.damageLossService.damageLossQueryService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossMetrics;
import com.JK.SIMS.repository.damageLossRepo.DamageLossRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DamageLossQueryService {
    private final DamageLossRepository damageLossRepository;

    // Helper method for internal use in services
    @Transactional(readOnly = true)
    public DamageLossMetrics getDamageLossMetrics() {
        try {
            return damageLossRepository.getDamageLossMetrics();
        } catch (DataAccessException de) {
            throw new DatabaseException("DL (getDamageLossMetrics): Failed to retrieve damage/loss metrics", de);
        } catch (Exception e) {
            throw new ServiceException("DL (getDamageLossMetrics): Unexpected error retrieving damage/loss metrics", e);
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
