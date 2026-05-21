package com.meridian.repository;

import com.meridian.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndRevokedAtIsNull(UUID userId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);
}
