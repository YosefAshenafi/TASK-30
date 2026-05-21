package com.meridian.repository;

import com.meridian.entity.DataPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataPermissionRepository extends JpaRepository<DataPermission, UUID> {

    List<DataPermission> findByUserId(UUID userId);

    Optional<DataPermission> findByUserIdAndFieldName(UUID userId, String fieldName);

    boolean existsByUserIdAndFieldNameAndClassificationIn(
            UUID userId, String fieldName, Collection<String> classifications);
}
