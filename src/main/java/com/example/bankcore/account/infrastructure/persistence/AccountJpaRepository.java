package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID>,
        JpaSpecificationExecutor<AccountEntity> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

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
