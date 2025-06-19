package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.damage_loss.DamageLoss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DamageLoss_repository extends JpaRepository<DamageLoss, Integer> {
}
