package com.example.bankcore.customer.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.application.CustomerService;
import com.example.bankcore.customer.config.CustomerProperties;
import com.example.bankcore.customer.web.dto.CreateCustomerRequest;
import com.example.bankcore.customer.web.dto.CustomerResponse;
import com.example.bankcore.customer.web.dto.CustomerSearchRequest;
import com.example.bankcore.customer.web.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final CustomerProperties properties;

    public CustomerController(CustomerService customerService, CustomerProperties properties) {
        this.customerService = customerService;
        this.properties = properties;
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
     * Searches customers, one page at a time.
     *
     * <p>Filters, sorting and paging all come from the query string; the metadata block reports
     * where the client is in the result set so it can page through it without guessing.
     */
    @GetMapping
    public ApiResponse<List<CustomerResponse>> search(@Valid @ModelAttribute CustomerSearchRequest request) {
        PageResult<CustomerResponse> page = customerService
                .search(request.toQuery(properties.defaultPageSize()))
                .map(CustomerResponse::from);

        return ApiResponse.success(page.content(), Map.of(
                "page", page.page(),
                "size", page.size(),
                "totalElements", page.totalElements(),
                "totalPages", page.totalPages(),
                "hasNext", page.hasNext()));
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
