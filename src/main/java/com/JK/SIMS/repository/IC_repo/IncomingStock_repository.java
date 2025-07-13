package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.damage_loss.DamageLoss;
import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomingStock_repository extends JpaRepository<IncomingStock, Long>, JpaSpecificationExecutor<IncomingStock> {

    boolean existsByPONumber(String potentialPONumber);

    @Query("SELECT isr FROM IncomingStock isr WHERE " +
            "LOWER(isr.product.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.supplier.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.orderedBy) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.updatedBy) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.PONumber) LIKE CONCAT('%', :text, '%')")
    Page<IncomingStock> searchProducts(String text, Pageable pageable);
}
