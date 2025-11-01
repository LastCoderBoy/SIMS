package com.JK.SIMS.repository.UserManagement_repo;

import com.JK.SIMS.models.UM_models.RefreshToken;
import com.JK.SIMS.models.UM_models.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUser(Users user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(Users user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteByExpiryDateBefore(Instant now);

    boolean existsByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllUserTokens(Users user);

    List<RefreshToken> findByUserAndRevokedFalse(Users user);

    // In RefreshTokenRepository
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user " +
            "AND rt.revoked = false AND rt.expiryDate > :now " +
            "ORDER BY rt.createdAt ASC")
    List<RefreshToken> findActiveTokensByUser(@Param("user") Users user, @Param("now") Instant now);
}
