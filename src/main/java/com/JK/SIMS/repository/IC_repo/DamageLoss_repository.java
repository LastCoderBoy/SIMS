package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.damage_loss.DamageLoss;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DamageLoss_repository extends JpaRepository<DamageLoss, Integer> {

    @Query("""
        SELECT new com.JK.SIMS.models.IC_models.damage_loss.DamageLossMetrics(
            COUNT(*),
            SUM(dl.quantityLost),
            SUM(dl.lossValue)
        )
        FROM DamageLoss dl
    """)
    DamageLossMetrics getDamageLossMetrics();
}
