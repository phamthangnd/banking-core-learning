package com.example.bankcore.account.web;

import com.example.bankcore.account.application.AccountService;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.web.dto.AccountRequests;
import com.example.bankcore.account.web.dto.AccountResponse;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.config.CustomerProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account endpoints.
 *
 * <p>Lifecycle changes are explicit commands ({@code /activate}, {@code /freeze}) rather than a
 * {@code PATCH} that sets a status field. A state machine has rules; letting a client write the
 * next state directly invites it to skip them, and the URL then says what actually happened,
 * which matters for an audit trail (Phase 07).
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;
    private final CustomerProperties customerProperties;

    public AccountController(AccountService accountService, CustomerProperties customerProperties) {
        this.accountService = accountService;
        this.customerProperties = customerProperties;
    }

    /** Opens an account for a customer. The account starts {@code PENDING}. */
    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<ApiResponse<AccountResponse>> open(
            @PathVariable UUID customerId,
            @Valid @RequestBody AccountRequests.OpenAccount request,
            UriComponentsBuilder uriBuilder) {

        AccountResponse opened = AccountResponse.from(accountService.open(request.toCommand(customerId)));
        URI location = uriBuilder.path("/api/v1/accounts/{id}").buildAndExpand(opened.id()).toUri();

        return ResponseEntity.created(location).body(ApiResponse.success(opened));
    }

    /** All accounts of one customer. Paginated like every other collection. */
    @GetMapping("/customers/{customerId}/accounts")
    public ApiResponse<List<AccountResponse>> listForCustomer(
            @PathVariable UUID customerId,
            @Valid @ModelAttribute AccountRequests.SearchAccounts request) {

        AccountSearchQuery query = new AccountSearchQuery(customerId, request.status(),
                request.accountType(), request.currency(),
                request.toPageRequest(customerProperties.defaultPageSize()));

        return page(accountService.search(query));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AccountResponse>> search(
            @Valid @ModelAttribute AccountRequests.SearchAccounts request) {

        AccountSearchQuery query = new AccountSearchQuery(request.customerId(), request.status(),
                request.accountType(), request.currency(),
                request.toPageRequest(customerProperties.defaultPageSize()));

        return page(accountService.search(query));
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<AccountResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(AccountResponse.from(accountService.getById(id)));
    }

    /** Lookup by the customer-facing number, which is what a teller has in front of them. */
    @GetMapping("/accounts/by-number/{accountNumber}")
    public ApiResponse<AccountResponse> getByNumber(@PathVariable String accountNumber) {
        return ApiResponse.success(AccountResponse.from(accountService.getByAccountNumber(accountNumber)));
    }

    @PostMapping("/accounts/{id}/activate")
    public ApiResponse<AccountResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(AccountResponse.from(accountService.activate(id)));
    }

    @PostMapping("/accounts/{id}/freeze")
    public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
        return ApiResponse.success(AccountResponse.from(accountService.freeze(id)));
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
        return ApiResponse.success(AccountResponse.from(accountService.unfreeze(id)));
    }

    @PutMapping("/accounts/{id}/overdraft-limit")
    public ApiResponse<AccountResponse> setOverdraftLimit(
            @PathVariable UUID id, @Valid @RequestBody AccountRequests.SetOverdraftLimit request) {
        return ApiResponse.success(
                AccountResponse.from(accountService.setOverdraftLimit(id, request.toCommand())));
    }

    /**
     * Closes an account.
     *
     * <p>{@code POST /close} rather than {@code DELETE}: nothing is deleted. The account stays,
     * its history stays, and the URL says what happened.
     */
    @PostMapping("/accounts/{id}/close")
    public ApiResponse<AccountResponse> close(@PathVariable UUID id) {
        return ApiResponse.success(AccountResponse.from(accountService.close(id)));
    }

    private static ApiResponse<List<AccountResponse>> page(PageResult<com.example.bankcore.account.domain.Account> result) {
        PageResult<AccountResponse> mapped = result.map(AccountResponse::from);

        return ApiResponse.success(mapped.content(), Map.of(
                "page", mapped.page(),
                "size", mapped.size(),
                "totalElements", mapped.totalElements(),
                "totalPages", mapped.totalPages(),
                "hasNext", mapped.hasNext()));
    }
}
