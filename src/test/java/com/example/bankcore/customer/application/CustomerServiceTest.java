package com.example.bankcore.customer.application;

import com.example.bankcore.customer.config.CustomerProperties;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerEmailAlreadyUsedException;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRuleViolationException;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import com.example.bankcore.customer.domain.CustomerStatus;
import com.example.bankcore.customer.domain.IllegalCustomerKycTransitionException;
import com.example.bankcore.customer.domain.KycStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Business rules of the customer module. The clock is fixed, so age rules and timestamps are
 * deterministic instead of depending on when the suite happens to run.
 */
class CustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(1990, 1, 1);

    private FakeCustomerRepository repository;
    private CustomerService service;

    /** No open accounts unless a test says otherwise. */
    private boolean customerHasOpenAccounts;

    @BeforeEach
    void setUp() {
        repository = new FakeCustomerRepository();
        customerHasOpenAccounts = false;
        // minimum age 18, default page size 20, max page size 5 (small, to test the cap)
        service = new CustomerService(repository, customerId -> customerHasOpenAccounts,
                new CustomerProperties(18, 20, 5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CustomerSearchQuery pageOf(int size) {
        return new CustomerSearchQuery(null, null, null,
                new PageRequest(0, size, "createdAt", SortDirection.ASC));
    }

    private static CustomerCommands.CreateCustomer createCommand(String email) {
        return new CustomerCommands.CreateCustomer("Alice Nguyen", email, "+84 90 123 4567", ADULT_BIRTH_DATE);
    }

    @Nested
    class Create {

        @Test
        void shouldRegisterAnActiveCustomer() {
            Customer created = service.create(createCommand("alice@example.com"));

            assertThat(created.id()).isNotNull();
            assertThat(created.status()).isEqualTo(CustomerStatus.ACTIVE);
            assertThat(created.createdAt()).isEqualTo(NOW);
            assertThat(created.updatedAt()).isEqualTo(NOW);
            assertThat(repository.findById(created.id())).contains(created);
        }

        @Test
        void shouldNormalizeEmailBeforeStoring() {
            Customer created = service.create(createCommand("  Alice@Example.COM  "));

            assertThat(created.email()).isEqualTo("alice@example.com");
            assertThat(repository.findByEmail("alice@example.com")).isPresent();
        }

        @Test
        void shouldRejectDuplicateEmailRegardlessOfCase() {
            service.create(createCommand("alice@example.com"));

            assertThatThrownBy(() -> service.create(createCommand("ALICE@example.com")))
                    .isInstanceOf(CustomerEmailAlreadyUsedException.class);

            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectCustomerBelowMinimumAge() {
            var underage = new CustomerCommands.CreateCustomer(
                    "Bao Tran", "bao@example.com", "+84 90 000 0000", LocalDate.of(2010, 6, 16));

            assertThatThrownBy(() -> service.create(underage))
                    .isInstanceOf(CustomerRuleViolationException.class)
                    .hasMessageContaining("18");
        }

        @Test
        void shouldAcceptCustomerOnExactlyTheMinimumAge() {
            // Born exactly 18 years before the fixed "today".
            var justEighteen = new CustomerCommands.CreateCustomer(
                    "Bao Tran", "bao@example.com", "+84 90 000 0000", LocalDate.of(2008, 6, 15));

            assertThat(service.create(justEighteen).id()).isNotNull();
        }

        @Test
        void shouldRejectFutureBirthDate() {
            var timeTraveller = new CustomerCommands.CreateCustomer(
                    "Future Person", "future@example.com", "+84 90 000 0000", LocalDate.of(2030, 1, 1));

            assertThatThrownBy(() -> service.create(timeTraveller))
                    .isInstanceOf(CustomerRuleViolationException.class)
                    .hasMessageContaining("future");
        }

    }

    @Nested
    class Read {

        @Test
        void shouldFailWhenCustomerDoesNotExist() {
            UUID unknown = UUID.randomUUID();

            assertThatThrownBy(() -> service.getById(unknown))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining(unknown.toString());
        }

        @Test
        void shouldCapTheRequestedPageSize() {
            for (int i = 0; i < 8; i++) {
                service.create(createCommand("customer%d@example.com".formatted(i)));
            }

            // The client asks for 50 rows; the configured ceiling is 5.
            var page = service.search(pageOf(50));

            assertThat(page.content()).hasSize(5);
            assertThat(page.size()).isEqualTo(5);
            assertThat(page.totalElements()).isEqualTo(8);
            assertThat(page.totalPages()).isEqualTo(2);
            assertThat(page.hasNext()).isTrue();
        }

        @Test
        void shouldFilterByStatus() {
            var active = service.create(createCommand("active@example.com"));
            var closed = service.create(createCommand("closed@example.com"));
            service.close(closed.id());

            var page = service.search(new CustomerSearchQuery(null, null, CustomerStatus.ACTIVE,
                    new PageRequest(0, 5, "createdAt", SortDirection.ASC)));

            assertThat(page.content()).extracting(com.example.bankcore.customer.domain.Customer::id)
                    .containsExactly(active.id());
        }

        @Test
        void shouldReturnAnEmptyPageWhenNothingMatches() {
            var page = service.search(new CustomerSearchQuery("nobody", null, null,
                    new PageRequest(0, 5, "createdAt", SortDirection.ASC)));

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
            assertThat(page.totalPages()).isZero();
            assertThat(page.hasNext()).isFalse();
        }
    }

    @Nested
    class Update {

        @Test
        void shouldChangeContactDetailsOnly() {
            Customer created = service.create(createCommand("alice@example.com"));

            Customer updated = service.update(created.id(),
                    new CustomerCommands.UpdateCustomer("Alice Tran", "alice.tran@example.com", "+84 91 111 2222"));

            assertThat(updated.id()).isEqualTo(created.id());
            assertThat(updated.dateOfBirth()).isEqualTo(created.dateOfBirth());
            assertThat(updated.createdAt()).isEqualTo(created.createdAt());
            assertThat(updated.fullName()).isEqualTo("Alice Tran");
            assertThat(updated.email()).isEqualTo("alice.tran@example.com");
            assertThat(updated.phoneNumber()).isEqualTo("+84 91 111 2222");
        }

        @Test
        void shouldAllowKeepingTheOwnEmail() {
            Customer created = service.create(createCommand("alice@example.com"));

            Customer updated = service.update(created.id(),
                    new CustomerCommands.UpdateCustomer("Alice N.", "ALICE@example.com", "+84 91 111 2222"));

            assertThat(updated.email()).isEqualTo("alice@example.com");
        }

        @Test
        void shouldRejectAnEmailOwnedByAnotherCustomer() {
            Customer alice = service.create(createCommand("alice@example.com"));
            service.create(createCommand("bob@example.com"));

            assertThatThrownBy(() -> service.update(alice.id(),
                    new CustomerCommands.UpdateCustomer("Alice", "bob@example.com", "+84 91 111 2222")))
                    .isInstanceOf(CustomerEmailAlreadyUsedException.class);
        }

        @Test
        void shouldRejectUpdatingAClosedCustomer() {
            Customer created = service.create(createCommand("alice@example.com"));
            service.close(created.id());

            assertThatThrownBy(() -> service.update(created.id(),
                    new CustomerCommands.UpdateCustomer("Alice", "alice@example.com", "+84 91 111 2222")))
                    .isInstanceOf(CustomerRuleViolationException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        void shouldFailForUnknownCustomer() {
            assertThatThrownBy(() -> service.update(UUID.randomUUID(),
                    new CustomerCommands.UpdateCustomer("X", "x@example.com", "+84 91 111 2222")))
                    .isInstanceOf(CustomerNotFoundException.class);
        }
    }

    @Nested
    class Close {

        @Test
        void shouldCloseInsteadOfDeleting() {
            Customer created = service.create(createCommand("alice@example.com"));

            Customer closed = service.close(created.id());

            assertThat(closed.status()).isEqualTo(CustomerStatus.CLOSED);
            assertThat(repository.findById(created.id())).isPresent();
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void shouldBeIdempotent() {
            Customer created = service.create(createCommand("alice@example.com"));

            Customer first = service.close(created.id());
            Customer second = service.close(created.id());

            assertThat(second).isEqualTo(first);
            assertThat(second.updatedAt()).isEqualTo(first.updatedAt());
        }

        @Test
        void shouldFailForUnknownCustomer() {
            assertThatThrownBy(() -> service.close(UUID.randomUUID()))
                    .isInstanceOf(CustomerNotFoundException.class);
        }

        @Test
        void shouldRefuseToCloseACustomerHoldingOpenAccounts() {
            Customer created = service.create(createCommand("alice@example.com"));
            customerHasOpenAccounts = true;

            // Closing the owner of an open account would leave the account, and the money on it,
            // with nobody to claim them.
            assertThatThrownBy(() -> service.close(created.id()))
                    .isInstanceOf(CustomerRuleViolationException.class)
                    .hasMessageContaining("open accounts");

            assertThat(repository.findById(created.id()).orElseThrow().isClosed()).isFalse();
        }
    }

    @Nested
    class Kyc {

        @Test
        void shouldStartPending() {
            Customer created = service.create(createCommand("alice@example.com"));

            assertThat(created.kycStatus()).isEqualTo(KycStatus.PENDING);
            assertThat(created.isKycVerified()).isFalse();
            assertThat(created.kycReviewedAt()).isNull();
        }

        @Test
        void shouldRecordAVerification() {
            Customer created = service.create(createCommand("alice@example.com"));

            Customer verified = service.decideKyc(created.id(), KycStatus.VERIFIED);

            assertThat(verified.isKycVerified()).isTrue();
            assertThat(verified.kycReviewedAt()).isEqualTo(NOW);
        }

        @Test
        void shouldAllowResubmissionAfterRejection() {
            Customer created = service.create(createCommand("alice@example.com"));
            service.decideKyc(created.id(), KycStatus.REJECTED);

            assertThat(service.decideKyc(created.id(), KycStatus.PENDING).kycStatus())
                    .isEqualTo(KycStatus.PENDING);
        }

        @Test
        void shouldRefuseAnIllegalDecision() {
            Customer created = service.create(createCommand("alice@example.com"));
            service.decideKyc(created.id(), KycStatus.VERIFIED);

            // VERIFIED -> REJECTED is not a review outcome; re-verification goes through PENDING.
            assertThatThrownBy(() -> service.decideKyc(created.id(), KycStatus.REJECTED))
                    .isInstanceOf(IllegalCustomerKycTransitionException.class);
        }

        @Test
        void shouldRefuseToReviewAClosedCustomer() {
            Customer created = service.create(createCommand("alice@example.com"));
            service.close(created.id());

            assertThatThrownBy(() -> service.decideKyc(created.id(), KycStatus.VERIFIED))
                    .isInstanceOf(CustomerRuleViolationException.class);
        }

        @Test
        void shouldStoreAndClearAnAvatarReference() {
            Customer created = service.create(createCommand("alice@example.com"));
            UUID fileId = UUID.randomUUID();

            assertThat(service.setAvatar(created.id(), fileId).avatarFileId()).isEqualTo(fileId);
            assertThat(service.setAvatar(created.id(), null).avatarFileId()).isNull();
        }
    }
}
