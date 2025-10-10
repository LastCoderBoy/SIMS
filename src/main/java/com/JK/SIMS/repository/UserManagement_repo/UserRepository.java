package com.JK.SIMS.repository.UserManagement_repo;

import com.JK.SIMS.models.UM_models.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUsername(String username);

    @Query("SELECT u FROM Users u WHERE u.email = :login OR u.username = :login")
    Optional<Users> findByUsernameOrEmail(@Param("login") String login);
}
