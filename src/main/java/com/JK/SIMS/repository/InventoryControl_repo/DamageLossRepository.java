package com.JK.SIMS.repository.InventoryControl_repo;

import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.DamageLossMetrics;
import com.JK.SIMS.models.damage_loss.LossReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DamageLossRepository extends JpaRepository<DamageLoss, Integer> {

    @Query("""
        SELECT new com.JK.SIMS.models.damage_loss.DamageLossMetrics(
            COUNT(*),
            SUM(dl.quantityLost),
            SUM(dl.lossValue)
        )
        FROM DamageLoss dl
    """)
    DamageLossMetrics getDamageLossMetrics();

    @Query("SELECT dl FROM DamageLoss dl WHERE " +
            "LOWER(dl.icProduct.pmProduct.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(dl.icProduct.SKU) LIKE CONCAT('%', :text, '%')")
    Page<DamageLoss> searchProducts(String text, Pageable pageable);


    @Query("SELECT dl FROM DamageLoss dl WHERE dl.reason = :reason")
    Page<DamageLoss> findByReason(LossReason reason, Pageable pageable);
}
