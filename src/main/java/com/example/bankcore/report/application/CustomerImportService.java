package com.example.bankcore.report.application;

import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.customer.application.CustomerCommands;
import com.example.bankcore.customer.application.CustomerService;
import com.example.bankcore.common.exception.BusinessException;
import com.example.bankcore.report.domain.ImportReport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports customers from a spreadsheet.
 *
 * <p>Two decisions shape this:
 *
 * <ul>
 *   <li><b>Row-independent.</b> Each row is imported in its own transaction, so one bad row does
 *       not discard the four thousand good ones before it. The report says exactly which rows
 *       failed and why, which is what makes a re-upload of the fixed rows possible.</li>
 *   <li><b>The same service as the API.</b> Rows go through {@code CustomerService}, so an import
 *       cannot bypass a business rule the API enforces. An importer with its own validation is an
 *       importer that will eventually disagree with the application.</li>
 * </ul>
 */
@Service
public class CustomerImportService {

    /** A ceiling, so one upload cannot occupy the server indefinitely. */
    private static final int MAX_ROWS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(CustomerImportService.class);

    private final CustomerService customers;
    private final AuditService audit;

    public CustomerImportService(CustomerService customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('customer:import')")
    public ImportReport importCustomers(InputStream spreadsheet) {
        List<ImportReport.RowError> errors = new ArrayList<>();
        int total = 0;
        int imported = 0;

        try (Workbook workbook = new XSSFWorkbook(spreadsheet)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Row 0 is the header; the operator sees 1-based numbers, so report them that way.
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(row)) {
                    continue;
                }

                total++;
                if (total > MAX_ROWS) {
                    errors.add(new ImportReport.RowError(rowIndex + 1, null,
                            "Import stopped at the limit of %d rows".formatted(MAX_ROWS)));
                    break;
                }

                try {
                    customers.create(toCommand(row));
                    imported++;
                } catch (BusinessException rejected) {
                    // A business rule said no. That is a row problem, not an import problem.
                    errors.add(new ImportReport.RowError(rowIndex + 1, null, rejected.getMessage()));
                } catch (IllegalArgumentException | NullPointerException malformed) {
                    errors.add(new ImportReport.RowError(rowIndex + 1, null, malformed.getMessage()));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            audit.recordFailure("CUSTOMER_IMPORT", "CUSTOMER", null, "file could not be read");
            throw new ImportFailedException("The spreadsheet could not be read");
        }

        ImportReport report = new ImportReport(total, imported, errors);
        audit.record("CUSTOMER_IMPORT", "CUSTOMER", null,
                report.isCompletelySuccessful() ? com.example.bankcore.audit.domain.AuditOutcome.SUCCESS
                        : com.example.bankcore.audit.domain.AuditOutcome.FAILURE,
                "imported %d of %d rows".formatted(imported, total));

        log.info("Customer import finished: total={} imported={} failed={}",
                total, imported, report.failed());
        return report;
    }

    private static CustomerCommands.CreateCustomer toCommand(Row row) {
        return new CustomerCommands.CreateCustomer(
                text(row.getCell(0)), text(row.getCell(1)), text(row.getCell(2)), date(row.getCell(3)));
    }

    private static boolean isBlank(Row row) {
        for (int column = 0; column < 4; column++) {
            if (!text(row.getCell(column)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a cell as text.
     *
     * <p>Numeric cells are formatted without scientific notation: a phone number typed into a
     * spreadsheet frequently arrives as {@code 8.4901234567E10}, and importing that would store a
     * number nobody can call.
     */
    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static LocalDate date(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("Date of birth is missing");
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String raw = text(cell);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Date of birth must be formatted as yyyy-MM-dd");
        }
    }

    /** The file itself was unusable, as opposed to some of its rows. */
    public static class ImportFailedException extends BusinessException {

        private static final long serialVersionUID = 1L;

        public ImportFailedException(String message) {
            super(com.example.bankcore.common.api.ErrorCode.IMPORT_FAILED, message);
        }
    }
}
