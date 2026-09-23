package com.example.bankcore.customer.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.customer.application.CustomerService;
import com.example.bankcore.customer.web.dto.CreateCustomerRequest;
import com.example.bankcore.customer.web.dto.CustomerResponse;
import com.example.bankcore.customer.web.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer CRUD over HTTP.
 *
 * <p>The controller does four things and nothing else: bind the request, validate its shape,
 * delegate to the service, and map the result onto an HTTP status. There is no business rule, no
 * try/catch and no persistence here — failures travel as exceptions to
 * {@code GlobalExceptionHandler} (CLAUDE.md section 2).
 *
 * <p>Authorization: none yet. Phase 03 introduces JWT authentication and RBAC and locks these
 * endpoints down; until then the module must not be exposed outside a development machine.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Creates a customer. Returns 201 with a {@code Location} header pointing at the new resource. */
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CreateCustomerRequest request,
            UriComponentsBuilder uriBuilder) {

        CustomerResponse created = CustomerResponse.from(customerService.create(request.toCommand()));
        URI location = uriBuilder.path("/api/v1/customers/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(ApiResponse.success(created));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(CustomerResponse.from(customerService.getById(id)));
    }

    /**
     * Lists customers.
     *
     * <p>The result is capped by {@code bankcore.customer.max-list-size} and the metadata says so,
     * so a client can tell "this is everything" from "this is a truncated view". Real pagination
     * comes with the database in Phase 02 (CLAUDE.md section 5).
     */
    @GetMapping
    public ApiResponse<List<CustomerResponse>> list() {
        List<CustomerResponse> customers = customerService.list().stream()
                .map(CustomerResponse::from)
                .toList();

        return ApiResponse.success(customers, Map.of(
                "count", customers.size(),
                "paginated", false));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.success(CustomerResponse.from(customerService.update(id, request.toCommand())));
    }

    /**
     * Closes a customer.
     *
     * <p>This is a soft close, not a delete: the record is kept and marked {@code CLOSED}.
     * Repeating the call on an already closed customer returns 204 again, so the operation is
     * idempotent.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable UUID id) {
        customerService.close(id);
    }
}
