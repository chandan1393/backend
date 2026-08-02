package com.assignease.repository;

import com.assignease.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Case-insensitive lookups — email case is not significant and older rows
     *  may have been stored with mixed case before normalisation was added. */
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String resetToken);
    long countByRole(User.Role role);
}
