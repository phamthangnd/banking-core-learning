package com.example.bankcore.masterdata.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.masterdata.application.MasterDataService;
import com.example.bankcore.masterdata.domain.MasterDataEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reference data: branches, document types, currencies, transaction categories. */
@RestController
@RequestMapping("/api/v1/master-data")
public class MasterDataController {

    private final MasterDataService masterData;

    public MasterDataController(MasterDataService masterData) {
        this.masterData = masterData;
    }

    @GetMapping("/types")
    public ApiResponse<List<String>> types() {
        return ApiResponse.success(masterData.types());
    }

    /** @param includeInactive retired entries are excluded unless asked for, since they cannot be chosen */
    @GetMapping("/{type}")
    public ApiResponse<List<MasterDataResponse>> byType(
            @PathVariable String type,
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        return ApiResponse.success(masterData.byType(type, !includeInactive)
                .stream().map(MasterDataResponse::from).toList());
    }

    @GetMapping("/{type}/{code}")
    public ApiResponse<MasterDataResponse> byCode(@PathVariable String type, @PathVariable String code) {
        return ApiResponse.success(MasterDataResponse.from(masterData.byCode(type, code)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MasterDataResponse>> create(
            @Valid @RequestBody CreateEntryRequest request) {

        MasterDataEntry created = masterData.create(request.type(), request.code(),
                request.label(), request.description(), request.sortOrder() == null ? 0 : request.sortOrder());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(MasterDataResponse.from(created)));
    }

    @PutMapping("/{id}")
    public ApiResponse<MasterDataResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateEntryRequest request) {
        return ApiResponse.success(MasterDataResponse.from(masterData.update(id, request.label(),
                request.description(), request.sortOrder() == null ? 0 : request.sortOrder(),
                request.active() == null || request.active())));
    }

    /** @param code stable key; it is identity and cannot be changed afterwards */
    public record CreateEntryRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+", message = "may contain letters, digits and underscores")
            @Size(max = 40) String type,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+", message = "may contain letters, digits and underscores")
            @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String label,
            @Size(max = 500) String description,
            Integer sortOrder) {
    }

    public record UpdateEntryRequest(
            @NotBlank @Size(max = 150) String label,
            @Size(max = 500) String description,
            Integer sortOrder,
            Boolean active) {
    }

    public record MasterDataResponse(UUID id, String type, String code, String label,
                                     String description, int sortOrder, boolean active,
                                     Instant updatedAt) {

        static MasterDataResponse from(MasterDataEntry entry) {
            return new MasterDataResponse(entry.id(), entry.type(), entry.code(), entry.label(),
                    entry.description(), entry.sortOrder(), entry.active(), entry.updatedAt());
        }
    }
}
