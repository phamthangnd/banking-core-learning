package com.example.bankcore.transaction.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.idempotency.RequestFingerprint;
import com.example.bankcore.customer.config.CustomerProperties;
import com.example.bankcore.transaction.application.IdempotentTransactions;
import com.example.bankcore.transaction.application.TransactionService;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionSearchQuery;
import com.example.bankcore.transaction.web.dto.TransactionRequests;
import com.example.bankcore.transaction.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money movement and transaction history.
 *
 * <p>Each operation is a POST to its own path because each one is a distinct financial command,
 * not an update of a resource. The response carries the transaction, including the balances as
 * they were immediately after posting.
 *
 * <p>Movements accept an optional {@code Idempotency-Key} header. A client that retries after a
 * timeout must send the same key with the same body; the retry is then answered with the original
 * transaction instead of moving the money again. Reusing a key with a different body is a 409,
 * because answering with the earlier result would tell the caller its new request had been
 * carried out.
 */
@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    /** Header a client sends to make a retry safe. */
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;
    private final IdempotentTransactions idempotentTransactions;
    private final CustomerProperties pagingDefaults;

    public TransactionController(TransactionService transactionService,
                                 IdempotentTransactions idempotentTransactions,
                                 CustomerProperties pagingDefaults) {
        this.transactionService = transactionService;
        this.idempotentTransactions = idempotentTransactions;
        this.pagingDefaults = pagingDefaults;
    }

    @PostMapping("/transactions/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody TransactionRequests.Deposit request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            UriComponentsBuilder uriBuilder) {

        // The fingerprint covers everything that decides the financial effect, so the same key
        // with a different amount or account is caught rather than replayed.
        String fingerprint = RequestFingerprint.of("deposit", request.accountId(),
                request.amount(), request.currency());

        return created(TransactionResponse.from(idempotentTransactions.execute(idempotencyKey, fingerprint,
                () -> transactionService.deposit(request.toCommand()))), uriBuilder);
    }

    @PostMapping("/transactions/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody TransactionRequests.Withdraw request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            UriComponentsBuilder uriBuilder) {

        String fingerprint = RequestFingerprint.of("withdraw", request.accountId(),
                request.amount(), request.currency());

        return created(TransactionResponse.from(idempotentTransactions.execute(idempotencyKey, fingerprint,
                () -> transactionService.withdraw(request.toCommand()))), uriBuilder);
    }

    @PostMapping("/transactions/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransactionRequests.Transfer request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            UriComponentsBuilder uriBuilder) {

        String fingerprint = RequestFingerprint.of("transfer", request.sourceAccountId(),
                request.targetAccountId(), request.amount(), request.currency());

        return created(TransactionResponse.from(idempotentTransactions.execute(idempotencyKey, fingerprint,
                () -> transactionService.transfer(request.toCommand()))), uriBuilder);
    }

    /**
     * Reverses a posted transaction with a compensating one.
     *
     * <p>Returns the compensating transaction; the original keeps its own record and is marked
     * REVERSED.
     */
    @PostMapping("/transactions/{id}/reverse")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverse(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TransactionRequests.Reverse request,
            UriComponentsBuilder uriBuilder) {

        String reason = request == null ? null : request.reason();
        return created(TransactionResponse.from(transactionService.reverse(id, reason)), uriBuilder);
    }

    @GetMapping("/transactions/{id}")
    public ApiResponse<TransactionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(TransactionResponse.from(transactionService.getById(id)));
    }

    /** Lookup by the reference a customer quotes. */
    @GetMapping("/transactions/by-reference/{reference}")
    public ApiResponse<TransactionResponse> getByReference(@PathVariable String reference) {
        return ApiResponse.success(TransactionResponse.from(transactionService.getByReference(reference)));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<TransactionResponse>> search(
            @Valid @ModelAttribute TransactionRequests.SearchTransactions request) {

        return page(transactionService.search(new TransactionSearchQuery(
                request.accountId(), request.type(), request.status(), request.from(), request.to(),
                request.toPageRequest(pagingDefaults.defaultPageSize()))));
    }

    /** One account's statement: everything that touched it, on either side, newest first. */
    @GetMapping("/accounts/{accountId}/transactions")
    public ApiResponse<List<TransactionResponse>> history(
            @PathVariable UUID accountId,
            @Valid @ModelAttribute TransactionRequests.SearchTransactions request) {

        return page(transactionService.search(new TransactionSearchQuery(
                accountId, request.type(), request.status(), request.from(), request.to(),
                request.toPageRequest(pagingDefaults.defaultPageSize()))));
    }

    private static ResponseEntity<ApiResponse<TransactionResponse>> created(
            TransactionResponse transaction, UriComponentsBuilder uriBuilder) {
        URI location = uriBuilder.path("/api/v1/transactions/{id}").buildAndExpand(transaction.id()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location)
                .body(ApiResponse.success(transaction));
    }

    private static ApiResponse<List<TransactionResponse>> page(PageResult<Transaction> result) {
        PageResult<TransactionResponse> mapped = result.map(TransactionResponse::from);

        return ApiResponse.success(mapped.content(), Map.of(
                "page", mapped.page(),
                "size", mapped.size(),
                "totalElements", mapped.totalElements(),
                "totalPages", mapped.totalPages(),
                "hasNext", mapped.hasNext()));
    }
}
