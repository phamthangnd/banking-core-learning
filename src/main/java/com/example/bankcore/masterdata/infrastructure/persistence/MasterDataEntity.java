package com.example.bankcore.masterdata.infrastructure.persistence;

import com.example.bankcore.masterdata.domain.MasterDataEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "master_data")
public class MasterDataEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private String type;

    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MasterDataEntity() {
    }

    private MasterDataEntity(MasterDataEntry entry) {
        this.id = entry.id();
        this.type = entry.type();
        this.code = entry.code();
        this.createdAt = entry.createdAt();
        applyState(entry);
    }

    static MasterDataEntity fromDomain(MasterDataEntry entry) {
        return new MasterDataEntity(entry);
    }

    void applyState(MasterDataEntry entry) {
        this.label = entry.label();
        this.description = entry.description();
        this.sortOrder = entry.sortOrder();
        this.active = entry.active();
        this.updatedAt = entry.updatedAt();
    }

    MasterDataEntry toDomain() {
        return new MasterDataEntry(id, type, code, label, description, sortOrder, active,
                createdAt, updatedAt);
    }
}
