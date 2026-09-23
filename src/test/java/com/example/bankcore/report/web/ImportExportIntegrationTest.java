package com.example.bankcore.report.web;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.transaction.application.TransactionCommands;
import com.example.bankcore.transaction.application.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bulk import with a row-level report, and statement export in both formats. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "77777777-7777-7777-7777-777777777777",
        authorities = {"customer:import", "customer:write", "customer:read", "report:export",
                "transaction:write", "transaction:read", "account:read", "masterdata:read"})
class ImportExportIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TransactionService transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();
    }

    /** Builds an .xlsx in memory with a header row plus the given data rows. */
    private static byte[] spreadsheet(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Customers");
            Row header = sheet.createRow(0);
            String[] columns = {"fullName", "email", "phoneNumber", "dateOfBirth"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode importRows(String[][] rows) throws Exception {
        byte[] file = spreadsheet(rows);

        String response = mockMvc.perform(multipart("/api/v1/reports/customers/import")
                        .file(new MockMultipartFile("file", "customers.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    @Test
    void shouldImportValidRows() throws Exception {
        JsonNode report = importRows(new String[][]{
                {"Alice Nguyen", "alice.import@example.com", "+84901234567", "1990-01-01"},
                {"Bob Tran", "bob.import@example.com", "+84901234568", "1991-02-02"}});

        assertThat(report.path("totalRows").asInt()).isEqualTo(2);
        assertThat(report.path("imported").asInt()).isEqualTo(2);
        assertThat(report.path("errors")).isEmpty();
    }

    @Test
    void shouldReportRowLevelErrorsAndStillImportTheGoodRows() throws Exception {
        JsonNode report = importRows(new String[][]{
                {"Valid Person", "valid@example.com", "+84901234567", "1990-01-01"},
                {"", "missing-name@example.com", "+84901234568", "1990-01-01"},
                {"Too Young", "young@example.com", "+84901234569", "2015-01-01"},
                {"Bad Date", "baddate@example.com", "+84901234570", "not-a-date"},
                {"Another Valid", "valid2@example.com", "+84901234571", "1992-03-03"}});

        // One bad row must not discard the good ones.
        assertThat(report.path("totalRows").asInt()).isEqualTo(5);
        assertThat(report.path("imported").asInt()).isEqualTo(2);
        assertThat(report.path("errors")).hasSize(3);

        // The operator needs the spreadsheet's own line numbers to fix them.
        assertThat(report.path("errors").get(0).path("rowNumber").asInt()).isEqualTo(3);
        assertThat(report.path("errors").get(1).path("rowNumber").asInt()).isEqualTo(4);
        assertThat(report.path("errors").get(1).path("message").asText()).contains("18");
        assertThat(report.path("errors").get(2).path("rowNumber").asInt()).isEqualTo(5);
        assertThat(report.path("errors").get(2).path("message").asText()).contains("yyyy-MM-dd");
    }

    @Test
    void shouldReportADuplicateEmailAsARowError() throws Exception {
        importRows(new String[][]{{"First", "dup@example.com", "+84901234567", "1990-01-01"}});

        JsonNode report = importRows(new String[][]{
                {"Second", "dup@example.com", "+84901234568", "1990-01-01"}});

        assertThat(report.path("imported").asInt()).isZero();
        assertThat(report.path("errors").get(0).path("message").asText()).contains("already registered");
    }

    @Test
    void shouldRejectAFileThatIsNotASpreadsheet() throws Exception {
        mockMvc.perform(multipart("/api/v1/reports/customers/import")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain",
                                "this is not a workbook".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMPORT_FAILED"));
    }

    @Test
    void shouldSkipBlankRows() throws Exception {
        JsonNode report = importRows(new String[][]{
                {"Real Person", "real@example.com", "+84901234567", "1990-01-01"},
                {"", "", "", ""}});

        assertThat(report.path("totalRows").asInt()).isEqualTo(1);
        assertThat(report.path("imported").asInt()).isEqualTo(1);
    }

    @Test
    void shouldExportAStatementAsExcel() throws Exception {
        Account account = seedAccountWithHistory(25);

        byte[] xlsx = mockMvc.perform(get("/api/v1/reports/accounts/{id}/statement.xlsx", account.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);

            // Header plus one row per transaction, and the header names the columns.
            assertThat(sheet.getLastRowNum()).isEqualTo(25);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reference");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).startsWith("TXN-");
        }
    }

    @Test
    void shouldExportAStatementAsPdf() throws Exception {
        Account account = seedAccountWithHistory(3);

        byte[] pdf = mockMvc.perform(get("/api/v1/reports/accounts/{id}/statement.pdf", account.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).hasSizeGreaterThan(500);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void shouldPageThroughAHistoryLongerThanOneBatch() throws Exception {
        // The export fetches 500 rows per round trip; this crosses the boundary, so a bug in the
        // keyset cursor would show up as a missing, repeated or truncated row.
        Account account = seedAccountWithHistory(520);

        byte[] xlsx = mockMvc.perform(get("/api/v1/reports/accounts/{id}/statement.xlsx", account.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(520);

            // No reference may appear twice: that is what a broken cursor produces.
            java.util.Set<String> references = new java.util.HashSet<>();
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                assertThat(references.add(sheet.getRow(row).getCell(0).getStringCellValue()))
                        .describedAs("duplicate reference at row %d", row)
                        .isTrue();
            }
        }
    }

    @Test
    void exportShouldRequireItsPermission() throws Exception {
        Account account = seedAccountWithHistory(1);

        mockMvc.perform(get("/api/v1/reports/accounts/{id}/statement.pdf", account.id())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("account:read"))))
                .andExpect(status().isForbidden());
    }

    private Account seedAccountWithHistory(int movements) {
        Customer owner = customers.save(Customer.register(UUID.randomUUID(), "Statement Owner",
                "statement@example.com", "+84901234567", LocalDate.of(1990, 1, 1), clock.instant()));

        Account account = accounts.save(Account.open(UUID.randomUUID(), "9004000000201", owner.id(),
                AccountType.CHECKING, "VND", Money.zero("VND"), clock.instant()).activate(clock.instant()));

        for (int i = 0; i < movements; i++) {
            transactions.deposit(new TransactionCommands.Deposit(
                    account.id(), new BigDecimal("10.00"), "VND", "seed " + i));
        }

        return account;
    }
}
