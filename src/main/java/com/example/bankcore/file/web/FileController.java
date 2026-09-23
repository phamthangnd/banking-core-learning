package com.example.bankcore.file.web;

import com.example.bankcore.auth.web.CurrentUser;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.file.application.FileService;
import com.example.bankcore.file.domain.FileCategory;
import com.example.bankcore.file.domain.InvalidFileException;
import com.example.bankcore.file.domain.StoredFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * File upload and download.
 *
 * <p>Downloads are always sent as an attachment with the stored content type, never as
 * {@code inline}: serving user-uploaded content inline lets an uploaded HTML or SVG file run
 * script in the application's origin, which is stored cross-site scripting. {@code nosniff} stops
 * a browser from guessing a different type than the one declared.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("category") FileCategory category,
            @RequestParam(value = "customerId", required = false) UUID customerId) {

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidFileException("Upload could not be read");
        }

        StoredFile stored = fileService.upload(category, file.getOriginalFilename(),
                file.getContentType(), content, CurrentUser.requireId(), customerId);

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(FileResponse.from(stored)));
    }

    @GetMapping("/{id}")
    public ApiResponse<FileResponse> metadata(@PathVariable UUID id) {
        return ApiResponse.success(FileResponse.from(fileService.metadata(id)));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        StoredFile file = fileService.metadata(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(fileService.download(id)));
    }

    @GetMapping
    public ApiResponse<List<FileResponse>> listForCustomer(@RequestParam UUID customerId) {
        return ApiResponse.success(fileService.listForCustomer(customerId)
                .stream().map(FileResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        fileService.delete(id);
    }

    /** Response body for file metadata. The storage key is internal and never exposed. */
    public record FileResponse(UUID id, String originalName, String contentType, long sizeBytes,
                               String checksum, FileCategory category, UUID customerId,
                               Instant createdAt) {

        static FileResponse from(StoredFile file) {
            return new FileResponse(file.id(), file.originalName(), file.contentType(),
                    file.sizeBytes(), file.checksum(), file.category(), file.customerId(),
                    file.createdAt());
        }
    }
}
