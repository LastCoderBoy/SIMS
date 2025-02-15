package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.InventoryData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IC_repository extends JpaRepository<InventoryData, String> {
}
