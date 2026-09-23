package com.example.bankcore.report.domain;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a bulk import, row by row.
 *
 * <p>An import that answers "failed" and nothing else is unusable: with five thousand rows the
 * operator needs to know which ones and why, fix those, and re-upload. Every rejected row carries
 * its line number and a readable reason.
 *
 * <p>Rows are independent. One bad row does not roll back the good ones — the alternative, all or
 * nothing, means a single typo in row 4 700 wastes the whole import.
 *
 * @param totalRows  data rows read, excluding the header
 * @param imported   rows that produced a record
 * @param errors     rejected rows, in file order
 */
public record ImportReport(int totalRows, int imported, List<RowError> errors) {

    public ImportReport {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    public int failed() {
        return errors.size();
    }

    public boolean isCompletelySuccessful() {
        return errors.isEmpty();
    }

    /**
     * @param rowNumber 1-based line number as the operator sees it in the spreadsheet
     * @param column    which column was wrong, or {@code null} when the whole row was
     * @param message   what to fix; safe to show, and it never echoes the value back
     */
    public record RowError(int rowNumber, String column, String message) {
    }
}
