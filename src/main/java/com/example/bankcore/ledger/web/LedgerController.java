package com.example.bankcore.ledger.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.ledger.application.LedgerService;
import com.example.bankcore.ledger.domain.LedgerEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ledger entries and reconciliation reports. */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** The balanced pair (or group) of entries behind one transaction. */
    @GetMapping("/transactions/{transactionId}/entries")
    public ApiResponse<List<LedgerEntryResponse>> entriesOfTransaction(@PathVariable UUID transactionId) {
        return ApiResponse.success(ledgerService.entriesOfTransaction(transactionId)
                .stream().map(LedgerEntryResponse::from).toList());
    }

    @GetMapping("/accounts/{accountId}/entries")
    public ApiResponse<List<LedgerEntryResponse>> entriesOfAccount(@PathVariable UUID accountId) {
        return ApiResponse.success(ledgerService.entriesOfAccount(accountId)
                .stream().map(LedgerEntryResponse::from).toList());
    }

    /** Whole-ledger reconciliation: are debits still equal to credits? */
    @GetMapping("/reconciliation")
    public ApiResponse<Map<String, Object>> reconcile() {
        LedgerService.LedgerReconciliation report = ledgerService.reconcile();

        return ApiResponse.success(Map.of(
                "balanced", report.balanced(),
                "debits", amounts(report.debits()),
                "credits", amounts(report.credits())));
    }

    /** One account: the stored balance against the balance the ledger implies. */
    @GetMapping("/accounts/{accountId}/reconciliation")
    public ApiResponse<Map<String, Object>> reconcileAccount(@PathVariable UUID accountId) {
        LedgerService.AccountReconciliation report = ledgerService.reconcileAccount(accountId);

        return ApiResponse.success(Map.of(
                "accountId", report.accountId(),
                "accountNumber", report.accountNumber(),
                "currency", report.currency(),
                "storedBalance", report.storedBalance().amount(),
                "derivedBalance", report.derivedBalance().amount(),
                "reconciled", report.reconciled()));
    }

    private static Map<String, BigDecimal> amounts(Map<String, com.example.bankcore.common.money.Money> totals) {
        return totals.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().amount(), (a, b) -> a));
    }

    /** Response body for a ledger entry. */
    public record LedgerEntryResponse(
            UUID id, UUID transactionId, int entryIndex, UUID accountId, String systemAccount,
            String direction, String currency, BigDecimal amount, BigDecimal balanceAfter,
            Instant createdAt) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.id(), entry.transactionId(), entry.entryIndex(),
                    entry.accountId(),
                    entry.systemAccount() == null ? null : entry.systemAccount().name(),
                    entry.direction().name(), entry.currency(), entry.amount().amount(),
                    entry.balanceAfter() == null ? null : entry.balanceAfter().amount(),
                    entry.createdAt());
        }
    }
}
