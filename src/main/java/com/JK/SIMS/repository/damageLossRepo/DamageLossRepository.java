package com.JK.SIMS.repository.damageLossRepo;

import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossMetrics;
import com.JK.SIMS.models.damage_loss.LossReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface DamageLossRepository extends JpaRepository<DamageLoss, Integer> {

    @Query("""
        SELECT new com.JK.SIMS.models.damage_loss.dtos.DamageLossMetrics(
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


    // ******* Report & Analytics related methods *******
    @Query(value = "SELECT SUM(quantity_lost) FROM damage_losses", nativeQuery = true)
    Long countTotalDamagedProducts();

    @Query("""
        SELECT COALESCE(SUM(dl.lossValue), 0)
        FROM DamageLoss dl
        WHERE dl.lossDate BETWEEN :startDate AND :endDate
    """)
    BigDecimal sumLossValueBetween(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

}
