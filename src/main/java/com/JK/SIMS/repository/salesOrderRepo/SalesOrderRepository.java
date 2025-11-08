package com.JK.SIMS.repository.salesOrderRepo;

import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.SalesOrderSummary;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT so FROM SalesOrder so WHERE so.orderReference LIKE CONCAT(:pattern, '%') ORDER BY so.orderReference DESC LIMIT 1")
    Optional<SalesOrder> findLatestSalesOrderWithPessimisticLock(@Param("pattern") String pattern);

    @Query(value = "SELECT COUNT(*) FROM sales_order WHERE status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')", nativeQuery = true)
    Long countOutgoingSalesOrders(); // Used in the Inventory Control

    @Query("SELECT so FROM SalesOrder so WHERE so.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')")
    Page<SalesOrder> findAllOutgoingSalesOrders(Pageable pageable);

    @Query("SELECT so FROM SalesOrder so WHERE so.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED') " +
            "AND so.estimatedDeliveryDate < :twoDaysFromNow")
    Page<SalesOrder> findAllUrgentSalesOrders(Pageable pageable, @Param("twoDaysFromNow") LocalDateTime twoDaysFromNow);

    @Query(value = "SELECT SUM(DATEDIFF(estimated_delivery_date, order_date)) " +
            "FROM sales_order WHERE status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')",
            nativeQuery = true)
    long calculateTotalDeliveryDate();

    @Query("""
    SELECT DISTINCT o FROM SalesOrder o
    JOIN o.items i
    JOIN i.product p
    WHERE o.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')
      AND (
        LOWER(o.customerName) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.orderReference) LIKE LOWER(CONCAT('%', :text, '%'))
       )
""")
    Page<SalesOrder> searchInWaitingSalesOrders(String text, Pageable pageable);

    @Query("""
    SELECT DISTINCT o FROM SalesOrder o
    JOIN o.items i
    JOIN i.product p
    WHERE LOWER(o.customerName) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.orderReference) LIKE LOWER(CONCAT('%', :text, '%'))
    """)
    Page<SalesOrder> searchInSalesOrders(String text, Pageable pageable);


    // ******* Report & Analytics related methods *******

    @Query(value = "SELECT COUNT(*) FROM sales_order WHERE status NOT IN ('DELIVERED', 'CANCELLED')", nativeQuery = true)
    Long countInProgressSalesOrders();

    @Query("""
    SELECT COUNT(*)
    FROM SalesOrder so
    WHERE so.orderDate BETWEEN :startDate AND :endDate
    AND so.status IN ('DELIVERED', 'COMPLETED')
""")
    Long countCompletedSalesOrdersBetween(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);


    @Query("""
    SELECT new com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.SalesOrderSummary(
        COUNT(CASE WHEN so.status = 'PENDING' THEN 1 END),
        COUNT(CASE WHEN so.status = 'DELIVERY_IN_PROCESS' THEN 1 END),
        COUNT(CASE WHEN so.status = 'DELIVERED' THEN 1 END),
        COUNT(CASE WHEN so.status = 'APPROVED' THEN 1 END),
        COUNT(CASE WHEN so.status = 'PARTIALLY_APPROVED' THEN 1 END),
        COUNT(CASE WHEN so.status = 'PARTIALLY_DELIVERED' THEN 1 END),
        COUNT(CASE WHEN so.status = 'CANCELLED' THEN 1 END))
    FROM SalesOrder so
""")
    SalesOrderSummary getSalesOrderSummaryMetrics();
}
