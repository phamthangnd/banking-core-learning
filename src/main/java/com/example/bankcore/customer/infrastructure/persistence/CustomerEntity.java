package com.example.bankcore.customer.infrastructure.persistence;

import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerStatus;
import com.example.bankcore.customer.domain.KycStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping of the {@code customers} table.
 *
 * <p>This entity is infrastructure, not the domain model. It is mutable because Hibernate needs
 * setters and a no-argument constructor to hydrate it, and it never leaves this package: the
 * adapter converts it to the immutable {@link Customer} record, and the web layer only ever sees
 * DTOs (CLAUDE.md section 2 — entities are never exposed from a REST API).
 *
 * <p>Design notes:
 * <ul>
 *   <li>The id is assigned by the application, not by the database. A caller can therefore know
 *       the identity before the row exists, which matters for idempotency keys in Phase 06.</li>
 *   <li>{@code @Enumerated(STRING)} stores {@code 'ACTIVE'}, not an ordinal. Ordinals silently
 *       corrupt data the moment someone reorders the enum constants.</li>
 *   <li>{@code @Version} enables optimistic locking: a concurrent update of a stale copy fails
 *       loudly instead of overwriting the other writer's change.</li>
 * </ul>
 */
@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_reviewed_at")
    private Instant kycReviewedAt;

    /** Reference into the file module (Phase 07); no foreign key, because that table does not exist yet. */
    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. Not for application code. */
    protected CustomerEntity() {
    }

    private CustomerEntity(Customer customer) {
        this.id = customer.id();
        applyState(customer);
        this.createdAt = customer.createdAt();
    }

    public static CustomerEntity fromDomain(Customer customer) {
        return new CustomerEntity(customer);
    }

    /**
     * Copies the mutable state of a domain customer onto this entity.
     *
     * <p>Called on a <em>managed</em> entity inside a transaction: Hibernate's dirty checking
     * notices the changed fields and issues the UPDATE at flush time. There is no explicit
     * update statement anywhere, which is the part of the persistence context that surprises
     * developers coming back to JPA.
     */
    public void applyState(Customer customer) {
        this.fullName = customer.fullName();
        this.email = customer.email();
        this.phoneNumber = customer.phoneNumber();
        this.dateOfBirth = customer.dateOfBirth();
        this.status = customer.status();
        this.kycStatus = customer.kycStatus();
        this.kycReviewedAt = customer.kycReviewedAt();
        this.avatarFileId = customer.avatarFileId();
        this.updatedAt = customer.updatedAt();
    }

    public Customer toDomain() {
        return new Customer(id, fullName, email, phoneNumber, dateOfBirth, status,
                kycStatus, kycReviewedAt, avatarFileId, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }
}
