package com.meridian.security.repository;

import com.meridian.security.entity.AllowedIpRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AllowedIpRangeRepository extends JpaRepository<AllowedIpRange, UUID> {

    @Query(value = "SELECT COUNT(*) FROM allowed_ip_ranges WHERE (role_scope IS NULL OR role_scope = :roleScope)",
           nativeQuery = true)
    long countRulesForRole(@Param("roleScope") String roleScope);

    @Query(value = "SELECT COUNT(*) > 0 FROM allowed_ip_ranges WHERE (role_scope IS NULL OR role_scope = :roleScope) AND CAST(:ip AS inet) <<= cidr",
           nativeQuery = true)
    boolean isIpAllowed(@Param("ip") String ip, @Param("roleScope") String roleScope);

    @Query(value = "SELECT CAST(cidr AS text) FROM allowed_ip_ranges WHERE (role_scope IS NULL OR role_scope = :roleScope) ORDER BY created_at",
           nativeQuery = true)
    List<String> findCidrsByRole(@Param("roleScope") String roleScope);
}
