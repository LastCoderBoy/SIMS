package com.JK.SIMS.repository.confirmationTokenRepo;

import com.JK.SIMS.models.IC_models.incoming.token.ConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfirmationTokenRepository extends JpaRepository<ConfirmationToken, Long> {

    Optional<ConfirmationToken> findByToken(String token);

    List<ConfirmationToken> findAllByExpiresAtBeforeAndClickedAtIsNull(LocalDateTime now);
}
