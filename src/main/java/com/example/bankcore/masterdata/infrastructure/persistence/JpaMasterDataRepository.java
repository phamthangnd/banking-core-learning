package com.example.bankcore.masterdata.infrastructure.persistence;

import com.example.bankcore.masterdata.domain.MasterDataEntry;
import com.example.bankcore.masterdata.domain.MasterDataRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JpaMasterDataRepository implements MasterDataRepository {

    private final MasterDataJpaRepository entries;

    public JpaMasterDataRepository(MasterDataJpaRepository entries) {
        this.entries = entries;
    }

    @Override
    @Transactional
    public MasterDataEntry save(MasterDataEntry entry) {
        MasterDataEntity entity = entries.findById(entry.id())
                .map(existing -> {
                    existing.applyState(entry);
                    return existing;
                })
                .orElseGet(() -> MasterDataEntity.fromDomain(entry));

        return entries.save(entity).toDomain();
    }

    @Override
    public Optional<MasterDataEntry> findById(UUID id) {
        return entries.findById(id).map(MasterDataEntity::toDomain);
    }

    @Override
    public Optional<MasterDataEntry> findByTypeAndCode(String type, String code) {
        return entries.findByTypeAndCode(type, code).map(MasterDataEntity::toDomain);
    }

    @Override
    public List<MasterDataEntry> findByType(String type, boolean activeOnly) {
        List<MasterDataEntity> found = activeOnly
                ? entries.findByTypeAndActiveTrueOrderBySortOrderAscLabelAsc(type)
                : entries.findByTypeOrderBySortOrderAscLabelAsc(type);

        return found.stream().map(MasterDataEntity::toDomain).toList();
    }

    @Override
    public List<String> findTypes() {
        return entries.findDistinctTypes();
    }
}
