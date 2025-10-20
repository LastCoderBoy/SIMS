package com.JK.SIMS.repository.SalesOrder_Repo;

import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
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
    Optional<SalesOrder> findLatestOrderWithPessimisticLock(@Param("pattern") String pattern);

    Page<SalesOrder> findByStatus(SalesOrderStatus status, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM sales_order WHERE status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')", nativeQuery = true)
    Long getWaitingStockSize();

    @Query("SELECT so FROM SalesOrder so WHERE so.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_DELIVERED')")
    Page<SalesOrder> findAllWaitingSalesOrders(Pageable pageable);

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
}
