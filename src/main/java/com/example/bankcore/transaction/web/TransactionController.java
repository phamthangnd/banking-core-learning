package com.example.bankcore.transaction.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.config.CustomerProperties;
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
 * <p>These endpoints are <b>not idempotent yet</b>: repeating a request posts a second
 * transaction. Idempotency keys arrive in Phase 06, and until then a client must not retry
 * blindly (CLAUDE.md section 3).
 */
@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;
    private final CustomerProperties pagingDefaults;

    public TransactionController(TransactionService transactionService, CustomerProperties pagingDefaults) {
        this.transactionService = transactionService;
        this.pagingDefaults = pagingDefaults;
    }

    @PostMapping("/transactions/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody TransactionRequests.Deposit request, UriComponentsBuilder uriBuilder) {
        return created(TransactionResponse.from(transactionService.deposit(request.toCommand())), uriBuilder);
    }

    @PostMapping("/transactions/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody TransactionRequests.Withdraw request, UriComponentsBuilder uriBuilder) {
        return created(TransactionResponse.from(transactionService.withdraw(request.toCommand())), uriBuilder);
    }

    @PostMapping("/transactions/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransactionRequests.Transfer request, UriComponentsBuilder uriBuilder) {
        return created(TransactionResponse.from(transactionService.transfer(request.toCommand())), uriBuilder);
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
