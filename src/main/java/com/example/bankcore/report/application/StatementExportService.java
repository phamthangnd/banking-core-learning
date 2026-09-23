package com.example.bankcore.report.application;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountExceptions;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.common.pagination.Cursor;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Account statements as a spreadsheet or a PDF.
 *
 * <p>The export never loads the whole history into memory. It walks the transactions in keyset
 * batches and writes each one straight to the response stream:
 *
 * <ul>
 *   <li><b>Keyset, not offset</b> — offset paging makes the database produce and discard every
 *       earlier row on each batch, so a long history costs time quadratic in its length.</li>
 *   <li><b>SXSSF, not XSSF</b> — the streaming workbook keeps a sliding window of rows in memory
 *       and flushes the rest to a temporary file. The ordinary writer builds the entire workbook
 *       in the heap, which is where "export worked in testing" turns into an out-of-memory error
 *       in production.</li>
 *   <li><b>Written to the response stream</b> — nothing accumulates a byte array of the result.</li>
 * </ul>
 */
@Service
public class StatementExportService {

    /** Rows fetched per round trip. Large enough to amortise the query, small enough to stay flat. */
    private static final int BATCH_SIZE = 500;

    /** Rows SXSSF keeps in memory before flushing to disk. */
    private static final int WINDOW_SIZE = 200;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final Logger log = LoggerFactory.getLogger(StatementExportService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AuditService audit;

    public StatementExportService(AccountRepository accounts, TransactionRepository transactions,
                                  AuditService audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('report:export')")
    public void exportToExcel(UUID accountId, OutputStream output) {
        Account account = requireAccount(accountId);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Statement");

            int rowIndex = 0;
            writeRow(sheet, rowIndex++, "Reference", "Date (UTC)", "Type", "Status",
                    "Currency", "Amount", "Balance after", "Description");

            for (Transaction transaction : eachTransaction(accountId)) {
                writeRow(sheet, rowIndex++,
                        transaction.reference(),
                        TIMESTAMP.format(transaction.occurredAt()),
                        transaction.type().name(),
                        transaction.status().name(),
                        transaction.currency(),
                        transaction.amount().amount().toPlainString(),
                        balanceAfterFor(transaction, accountId),
                        transaction.description() == null ? "" : transaction.description());
            }

            workbook.write(output);
            // close() on the try-with-resources deletes the temporary files SXSSF spilled rows
            // into; the old dispose() call is deprecated for exactly that reason.

            audit.recordSuccess("STATEMENT_EXPORT_XLSX", "ACCOUNT", accountId.toString());
            log.info("Statement exported: accountId={} format=xlsx rows={}", account.id(), rowIndex - 1);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * A PDF statement.
     *
     * <p>Paged as it is written, for the same reason: the document is built page by page from
     * batches rather than from a list of everything.
     */
    @PreAuthorize("hasAuthority('report:export')")
    public void exportToPdf(UUID accountId, OutputStream output) {
        Account account = requireAccount(accountId);

        try (PDDocument document = new PDDocument()) {
            PdfWriter writer = new PdfWriter(document);
            writer.startPage();
            writer.heading("Account statement");
            writer.line("Account: " + account.accountNumber());
            writer.line("Currency: " + account.currency());
            writer.line("Balance: " + account.balance().amount().toPlainString());
            writer.blankLine();
            writer.line(String.format("%-24s %-20s %-12s %14s", "Reference", "Date (UTC)", "Type", "Amount"));

            for (Transaction transaction : eachTransaction(accountId)) {
                writer.line(String.format("%-24s %-20s %-12s %14s",
                        transaction.reference(),
                        TIMESTAMP.format(transaction.occurredAt()),
                        transaction.type().name(),
                        transaction.amount().amount().toPlainString()));
            }

            writer.finish();
            document.save(output);

            audit.recordSuccess("STATEMENT_EXPORT_PDF", "ACCOUNT", accountId.toString());
            log.info("Statement exported: accountId={} format=pdf", account.id());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Walks an account's whole history in keyset batches.
     *
     * <p>An {@link Iterable} rather than a list: the caller consumes one transaction at a time and
     * the next batch is fetched only when the previous one is exhausted, so memory stays flat
     * however long the history is.
     */
    private Iterable<Transaction> eachTransaction(UUID accountId) {
        return () -> new java.util.Iterator<>() {

            private List<Transaction> batch = transactions.findAfter(accountId, null, BATCH_SIZE);
            private int index;

            @Override
            public boolean hasNext() {
                if (index < batch.size()) {
                    return true;
                }
                if (batch.size() < BATCH_SIZE) {
                    return false;
                }

                Transaction last = batch.get(batch.size() - 1);
                batch = transactions.findAfter(accountId,
                        new Cursor(last.occurredAt(), last.id()), BATCH_SIZE);
                index = 0;
                return !batch.isEmpty();
            }

            @Override
            public Transaction next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return batch.get(index++);
            }
        };
    }

    private Account requireAccount(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AccountExceptions.AccountNotFoundException(accountId));
    }

    /** The balance snapshot that belongs to this account's side of the movement. */
    private static String balanceAfterFor(Transaction transaction, UUID accountId) {
        var balance = accountId.equals(transaction.sourceAccountId())
                ? transaction.sourceBalanceAfter()
                : transaction.targetBalanceAfter();

        return balance == null ? "" : balance.amount().toPlainString();
    }

    private static void writeRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.length; column++) {
            row.createCell(column).setCellValue(values[column]);
        }
    }

    /** Minimal page-aware text writer, so the PDF does not need a layout library. */
    private static final class PdfWriter {

        private static final float MARGIN = 40;
        private static final float LINE_HEIGHT = 14;

        private final PDDocument document;
        private PDPageContentStream content;
        private float cursorY;

        private PdfWriter(PDDocument document) {
            this.document = document;
        }

        void startPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 9);
            content.beginText();
            cursorY = page.getMediaBox().getHeight() - MARGIN;
            content.newLineAtOffset(MARGIN, cursorY);
        }

        void heading(String text) throws IOException {
            line(text);
            blankLine();
        }

        void line(String text) throws IOException {
            if (cursorY <= MARGIN + LINE_HEIGHT) {
                finish();
                startPage();
            }

            content.showText(text == null ? "" : sanitize(text));
            content.newLineAtOffset(0, -LINE_HEIGHT);
            cursorY -= LINE_HEIGHT;
        }

        void blankLine() throws IOException {
            line("");
        }

        void finish() throws IOException {
            content.endText();
            content.close();
        }

        /** The standard 14 fonts cannot encode every character; drop what they cannot render. */
        private static String sanitize(String text) {
            return text.replaceAll("[^\\x20-\\x7E]", "?");
        }
    }
}
