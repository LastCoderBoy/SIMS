package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomingStock_repository extends JpaRepository<IncomingStock, Long> {

    boolean existsByPONumber(String potentialPONumber);
}
