package com.JK.SIMS.repository.PurchaseOrder_repo;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.PurchaseOrderSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    @Query(value = "SELECT COUNT(*) FROM purchase_order WHERE status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL') ", nativeQuery = true)
    Long countIncomingPurchaseOrders();

    @Query("SELECT po FROM PurchaseOrder po WHERE " +
            "po.status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL')")
    Page<PurchaseOrder> findAllPendingOrders(Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po " +
            "WHERE po.status IN ('DELIVERY_IN_PROCESS', 'PARTIALLY_RECEIVED', 'AWAITING_APPROVAL')" +
            "AND po.expectedArrivalDate < CURRENT DATE ")
    Page<PurchaseOrder> findAllOverdueOrders(Pageable pageable);


    // ******* Report & Analytics related methods *******
    @Query("""
    SELECT new com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.PurchaseOrderSummary(
        COUNT(CASE WHEN po.status = 'AWAITING_APPROVAL' THEN 1 END),
        COUNT(CASE WHEN po.status = 'DELIVERY_IN_PROCESS' THEN 1 END),
        COUNT(CASE WHEN po.status = 'PARTIALLY_RECEIVED' THEN 1 END),
        COUNT(CASE WHEN po.status = 'RECEIVED' THEN 1 END),
        COUNT(CASE WHEN po.status = 'CANCELLED' THEN 1 END),
        COUNT(CASE WHEN po.status = 'FAILED' THEN 1 END))
    FROM PurchaseOrder po
""")
    PurchaseOrderSummary getPurchaseOrderSummaryMetrics();

    List<PurchaseOrder> findByProduct_ProductID(String productId);

    List<PurchaseOrder> findBySupplier_Id(Long supplierId);
}
