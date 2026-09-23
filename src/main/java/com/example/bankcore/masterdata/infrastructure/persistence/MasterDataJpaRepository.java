package com.example.bankcore.masterdata.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterDataJpaRepository extends JpaRepository<MasterDataEntity, UUID> {

    Optional<MasterDataEntity> findByTypeAndCode(String type, String code);

    List<MasterDataEntity> findByTypeOrderBySortOrderAscLabelAsc(String type);

    List<MasterDataEntity> findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(String type);

    @Query("select distinct e.type from MasterDataEntity e order by e.type")
    List<String> findDistinctTypes();
}
