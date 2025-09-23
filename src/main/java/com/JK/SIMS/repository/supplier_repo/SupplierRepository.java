package com.JK.SIMS.repository.supplier_repo;

import com.JK.SIMS.models.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @Query("SELECT s FROM Supplier s WHERE s.name = ?1")
    Optional<Supplier> findByName(String name);

    @Query("SELECT s FROM Supplier s WHERE s.email = ?1")
    Optional<Supplier> findByEmail(String email);
}
