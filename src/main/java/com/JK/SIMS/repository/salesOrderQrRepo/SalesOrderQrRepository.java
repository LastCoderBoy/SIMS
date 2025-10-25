package com.JK.SIMS.repository.salesOrderQrRepo;

import com.JK.SIMS.models.salesOrder.qrcode.SalesOrderQRCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderQrRepository extends JpaRepository<SalesOrderQRCode, Long> {

    @Query("SELECT q FROM SalesOrderQRCode q WHERE q.qrToken = :token")
    Optional<SalesOrderQRCode> findByToken(@Param("token") String token);
}
