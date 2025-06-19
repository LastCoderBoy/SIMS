package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.outgoing.OutgoingStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutgoingStock_repository extends JpaRepository<OutgoingStock, String> {

}
