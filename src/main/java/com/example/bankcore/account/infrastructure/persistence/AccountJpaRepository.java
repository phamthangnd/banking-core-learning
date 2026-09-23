package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID>,
        JpaSpecificationExecutor<AccountEntity> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    /**
     * {@code SELECT ... FOR UPDATE}: the row is locked until the transaction ends, so a second
     * transaction touching the same account waits rather than racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCustomerIdAndStatusNot(UUID customerId, AccountStatus status);

    /**
     * Takes the next account number from the database sequence.
     *
     * <p>{@code nextval} is transactional-safe and never returns the same value twice, even
     * across concurrent transactions and even if one of them rolls back. That is exactly the
     * guarantee an account number needs, and it is not something application code can provide.
     */
    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSequence();
}
