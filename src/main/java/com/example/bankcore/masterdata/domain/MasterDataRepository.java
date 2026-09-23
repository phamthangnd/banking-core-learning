package com.example.bankcore.masterdata.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for reference data. */
public interface MasterDataRepository {

    MasterDataEntry save(MasterDataEntry entry);

    Optional<MasterDataEntry> findById(UUID id);

    Optional<MasterDataEntry> findByTypeAndCode(String type, String code);

    /** Entries of one catalogue, in presentation order. */
    List<MasterDataEntry> findByType(String type, boolean activeOnly);

    List<String> findTypes();
}
