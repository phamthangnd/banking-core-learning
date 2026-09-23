package com.example.bankcore.transaction.web;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.domain.KycStatus;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money movement end to end: the success paths and, just as importantly, the failure paths.
 *
 * <p>The assertions that matter most are the ones about what did <em>not</em> happen — that a
 * rejected transfer left both balances untouched, and that no balance ever changed without a
 * transaction row to account for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "55555555-5555-5555-5555-555555555555",
        authorities = {"transaction:read", "transaction:write", "account:read"})
class TransactionApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Clock clock;

    private Account first;
    private Account second;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();

        Customer owner = customers.save(Customer.register(UUID.randomUUID(), "Money Owner",
                        "money@example.com", "+84901234567", LocalDate.of(1990, 1, 1), clock.instant())
                .withKycStatus(KycStatus.VERIFIED, clock.instant()));

        first = activeAccount(owner.id(), "9004000000001", "VND");
        second = activeAccount(owner.id(), "9004000000002", "VND");
    }

    private Account activeAccount(UUID ownerId, String number, String currency) {
        return accounts.save(Account.open(UUID.randomUUID(), number, ownerId,
                        AccountType.CHECKING, currency, Money.zero(currency), clock.instant())
                .activate(clock.instant()));
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.id()).orElseThrow().balance().amount();
    }

    /** Named to avoid shadowing MockMvcRequestBuilders.post, which it uses internally. */
    private JsonNode postJson(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    private JsonNode deposit(Account account, String amount) throws Exception {
        return postJson("/api/v1/transactions/deposit",
                """
                {"accountId": "%s", "amount": %s, "currency": "VND", "description": "cash in"}
                """.formatted(account.id(), amount));
    }

    @Nested
    class Deposits {

        @Test
        void shouldCreditTheAccountAndRecordTheTransaction() throws Exception {
            JsonNode transaction = deposit(first, "1000.00");

            assertThat(transaction.path("type").asText()).isEqualTo("DEPOSIT");
            assertThat(transaction.path("status").asText()).isEqualTo("POSTED");
            assertThat(transaction.path("reference").asText()).startsWith("TXN-");
            assertThat(transaction.path("targetBalanceAfter").decimalValue()).isEqualByComparingTo("1000.0000");
            // Null fields are omitted from the envelope, so an absent source is a missing node.
            assertThat(transaction.path("sourceAccountId").isMissingNode()).isTrue();

            assertThat(balanceOf(first)).isEqualByComparingTo("1000.00");
        }

        @Test
        void shouldRejectADepositIntoAFrozenAccount() throws Exception {
            accounts.save(accounts.findById(first.id()).orElseThrow().freeze(clock.instant()));

            mockMvc.perform(post("/api/v1/transactions/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 10, "currency": "VND"}
                                    """.formatted(first.id())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("TRANSACTION_REJECTED"));

            assertThat(balanceOf(first)).isEqualByComparingTo("0");
        }

        @Test
        void shouldRejectAnAmountInAnotherCurrency() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 10, "currency": "USD"}
                                    """.formatted(first.id())))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(balanceOf(first)).isEqualByComparingTo("0");
        }

        @Test
        void shouldRejectANonPositiveAmountAtTheBoundary() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 0, "currency": "VND"}
                                    """.formatted(first.id())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    class Withdrawals {

        @Test
        void shouldDebitTheAccount() throws Exception {
            deposit(first, "1000.00");

            JsonNode withdrawal = postJson("/api/v1/transactions/withdraw",
                    """
                    {"accountId": "%s", "amount": 250.50, "currency": "VND"}
                    """.formatted(first.id()));

            assertThat(withdrawal.path("sourceBalanceAfter").decimalValue()).isEqualByComparingTo("749.5000");
            assertThat(balanceOf(first)).isEqualByComparingTo("749.50");
        }

        @Test
        void shouldValidateAvailableFunds() throws Exception {
            deposit(first, "100.00");

            mockMvc.perform(post("/api/v1/transactions/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 100.01, "currency": "VND"}
                                    """.formatted(first.id())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("TRANSACTION_REJECTED"));

            // The refusal must leave the balance exactly as it was.
            assertThat(balanceOf(first)).isEqualByComparingTo("100.00");
        }

        @Test
        void shouldRecordTheRejectionInTheHistory() throws Exception {
            deposit(first, "10.00");

            mockMvc.perform(post("/api/v1/transactions/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 5000, "currency": "VND"}
                                    """.formatted(first.id())))
                    .andExpect(status().isUnprocessableEntity());

            // A failed attempt is part of the history: the rollback of the refusal must not take
            // the record of it with it.
            mockMvc.perform(get("/api/v1/transactions").param("status", "FAILED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].failureReason",
                            org.hamcrest.Matchers.containsString("Insufficient")))
                    .andExpect(jsonPath("$.data[0].postedAt").doesNotExist());
        }
    }

    @Nested
    class Transfers {

        @Test
        void shouldMoveMoneyAtomically() throws Exception {
            deposit(first, "1000.00");

            JsonNode transfer = postJson("/api/v1/transactions/transfer",
                    """
                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 400, "currency": "VND"}
                    """.formatted(first.id(), second.id()));

            assertThat(transfer.path("type").asText()).isEqualTo("TRANSFER");
            assertThat(transfer.path("sourceBalanceAfter").decimalValue()).isEqualByComparingTo("600.0000");
            assertThat(transfer.path("targetBalanceAfter").decimalValue()).isEqualByComparingTo("400.0000");

            // Nothing is created and nothing is destroyed: the two sides still sum to the deposit.
            assertThat(balanceOf(first).add(balanceOf(second))).isEqualByComparingTo("1000.00");
        }

        @Test
        void shouldLeaveBothBalancesUntouchedWhenItFails() throws Exception {
            deposit(first, "100.00");
            deposit(second, "50.00");

            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 1000, "currency": "VND"}
                                    """.formatted(first.id(), second.id())))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(balanceOf(first)).isEqualByComparingTo("100.00");
            assertThat(balanceOf(second)).isEqualByComparingTo("50.00");
        }

        @Test
        void shouldRefuseATransferIntoAFrozenAccount() throws Exception {
            deposit(first, "100.00");
            accounts.save(accounts.findById(second.id()).orElseThrow().freeze(clock.instant()));

            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10, "currency": "VND"}
                                    """.formatted(first.id(), second.id())))
                    .andExpect(status().isUnprocessableEntity());

            // The debit must be rolled back with the credit that could not happen.
            assertThat(balanceOf(first)).isEqualByComparingTo("100.00");
        }

        @Test
        void shouldRefuseATransferToTheSameAccount() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10, "currency": "VND"}
                                    """.formatted(first.id(), first.id())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.details[0].field").value("targetAccountIdDifferent"));
        }

        @Test
        void shouldRefuseACrossCurrencyTransfer() throws Exception {
            Account usd = activeAccount(first.customerId(), "9004000000003", "USD");
            deposit(first, "100.00");

            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10, "currency": "VND"}
                                    """.formatted(first.id(), usd.id())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message",
                            org.hamcrest.Matchers.containsString("Cross-currency")));

            assertThat(balanceOf(first)).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    class History {

        @Test
        void shouldListEverythingThatTouchedAnAccount() throws Exception {
            deposit(first, "1000.00");
            postJson("/api/v1/transactions/transfer",
                    """
                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 100, "currency": "VND"}
                    """.formatted(first.id(), second.id()));

            // Money in and money out, in one paginated statement.
            mockMvc.perform(get("/api/v1/accounts/{id}/transactions", first.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadata.totalElements").value(2));

            mockMvc.perform(get("/api/v1/accounts/{id}/transactions", second.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadata.totalElements").value(1))
                    .andExpect(jsonPath("$.data[0].type").value("TRANSFER"));
        }

        @Test
        void shouldFilterByType() throws Exception {
            deposit(first, "100.00");
            postJson("/api/v1/transactions/withdraw",
                    """
                    {"accountId": "%s", "amount": 10, "currency": "VND"}
                    """.formatted(first.id()));

            mockMvc.perform(get("/api/v1/transactions").param("type", "WITHDRAWAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        void shouldLookUpByReference() throws Exception {
            String reference = deposit(first, "100.00").path("reference").asText();

            mockMvc.perform(get("/api/v1/transactions/by-reference/{reference}", reference))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reference").value(reference));

            mockMvc.perform(get("/api/v1/transactions/by-reference/{reference}", "TXN-19700101-000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));
        }
    }

    @Nested
    class Authorization {

        @Test
        @WithMockUser(authorities = "transaction:read")
        void readOnlyUserShouldNotMoveMoney() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accountId": "%s", "amount": 10, "currency": "VND"}
                                    """.formatted(first.id())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

            assertThat(balanceOf(first)).isEqualByComparingTo("0");
        }

        @Test
        @WithMockUser(authorities = "account:read")
        void userWithoutTransactionPermissionsShouldNotReadHistory() throws Exception {
            mockMvc.perform(get("/api/v1/transactions"))
                    .andExpect(status().isForbidden());
        }
    }
}
