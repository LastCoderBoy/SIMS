package com.JK.SIMS.repository.PurchaseOrder_repo;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    boolean existsByPONumber(String potentialPONumber);

    @Query("SELECT isr FROM PurchaseOrder isr WHERE " +
            "LOWER(isr.product.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.supplier.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(isr.PONumber) LIKE CONCAT('%', :text, '%')")
    Page<PurchaseOrder> searchOrders(String text, Pageable pageable);

    @Query("""
            SELECT isr FROM PurchaseOrder isr
            WHERE isr.status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL')
            AND (
                LOWER(isr.product.name) LIKE LOWER(CONCAT('%', :text, '%')) OR
                LOWER(isr.supplier.name) LIKE LOWER(CONCAT('%', :text, '%')) OR
                LOWER(isr.orderedBy) LIKE LOWER(CONCAT('%', :text, '%')) OR
                LOWER(isr.updatedBy) LIKE LOWER(CONCAT('%', :text, '%')) OR
                LOWER(isr.PONumber) LIKE LOWER(CONCAT('%', :text, '%'))
                )
            """)
    Page<PurchaseOrder> searchInPendingOrders(String text, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM purchase_order WHERE status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED') ", nativeQuery = true)
    Long getTotalValidPoSize();

    @Query("SELECT po FROM PurchaseOrder po WHERE " +
            "po.status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL')")
    Page<PurchaseOrder> findAllPendingOrders(Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po " +
            "WHERE po.status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL')" +
            "AND po.expectedArrivalDate < CURRENT DATE ")
    Page<PurchaseOrder> findAllOverdueOrders(Pageable pageable);
}
