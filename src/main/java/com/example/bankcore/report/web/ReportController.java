package com.example.bankcore.report.web;

import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.report.application.CustomerImportService;
import com.example.bankcore.report.application.StatementExportService;
import com.example.bankcore.report.domain.ImportReport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Bulk import and statement export.
 *
 * <p>Exports write straight to the servlet's output stream rather than returning a byte array.
 * Returning bytes would mean the whole statement exists in the heap before the first byte reaches
 * the client, which is precisely what the streaming export is built to avoid.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final CustomerImportService customerImport;
    private final StatementExportService statementExport;

    public ReportController(CustomerImportService customerImport, StatementExportService statementExport) {
        this.customerImport = customerImport;
        this.statementExport = statementExport;
    }

    /**
     * Imports customers from an .xlsx file.
     *
     * <p>Answers 200 with a row-level report even when rows failed: the upload itself succeeded,
     * and the operator needs the list of what to fix. A 4xx would suggest the file was rejected.
     */
    @PostMapping("/customers/import")
    public ApiResponse<ImportReport> importCustomers(@RequestPart("file") MultipartFile file) {
        try {
            return ApiResponse.success(customerImport.importCustomers(file.getInputStream()));
        } catch (IOException ex) {
            throw new CustomerImportService.ImportFailedException("The upload could not be read");
        }
    }

    @GetMapping("/accounts/{accountId}/statement.xlsx")
    public void statementAsExcel(@PathVariable UUID accountId, HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("statement-" + accountId + ".xlsx").build().toString());

        try {
            statementExport.exportToExcel(accountId, response.getOutputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @GetMapping("/accounts/{accountId}/statement.pdf")
    public void statementAsPdf(@PathVariable UUID accountId, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("statement-" + accountId + ".pdf").build().toString());

        try {
            statementExport.exportToPdf(accountId, response.getOutputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
