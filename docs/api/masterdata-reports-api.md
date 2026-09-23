# Master Data and Reports API

## Master data — `/api/v1/master-data`

Editable reference data: branches, document types, currencies, transaction categories. Distinct
from roles and permissions, which are part of the application's contract and live in a migration.

| Method | Path | Permission |
|---|---|---|
| GET | `/master-data/types` | `masterdata:read` |
| GET | `/master-data/{type}?includeInactive=` | `masterdata:read` |
| GET | `/master-data/{type}/{code}` | `masterdata:read` |
| POST | `/master-data` | `masterdata:write` |
| PUT | `/master-data/{id}` | `masterdata:write` |

The `code` is identity and cannot be changed: other records reference it, and renaming it would
silently repoint them. Retiring an entry sets `active = false` rather than deleting it — existing
records still point at it, and last year's report has to keep making sense.

Reads are cached and the cache is evicted on every write, so a change is visible immediately.
Reference data that is stale for five minutes produces support tickets nobody can reproduce.

## Import — `POST /api/v1/reports/customers/import`

Requires `customer:import`. Takes an `.xlsx` file with the columns
`fullName | email | phoneNumber | dateOfBirth` and a header row.

```json
{ "totalRows": 5, "imported": 2,
  "errors": [
    { "rowNumber": 3, "column": null, "message": "fullName must not be blank" },
    { "rowNumber": 4, "column": null, "message": "Customer must be at least 18 years old" },
    { "rowNumber": 5, "column": null, "message": "Date of birth must be formatted as yyyy-MM-dd" }
  ] }
```

- **Rows are independent.** One bad row does not roll back the good ones; a single typo in row
  4 700 must not waste the whole import.
- **Row numbers are the operator's**, 1-based as the spreadsheet shows them, so the failures can
  be found and fixed.
- **The same service as the API.** Rows go through `CustomerService`, so an import cannot bypass a
  rule the API enforces.
- Answers **200 even when rows failed**: the upload succeeded and the report is the result. A 4xx
  would suggest the file was rejected — which is what `IMPORT_FAILED` means, and only happens when
  the file cannot be read at all.
- Capped at 10 000 rows per upload.

## Export — `/api/v1/reports/accounts/{accountId}/statement.{xlsx,pdf}`

Requires `report:export`. Both formats stream.

| Decision | Why |
|---|---|
| **Keyset paging** over the history | `OFFSET` makes the database produce and discard every earlier row, so a long history costs time quadratic in its length |
| **SXSSF**, not XSSF | The streaming writer keeps a sliding window of rows in memory and spills the rest to disk; the ordinary writer builds the whole workbook in the heap |
| Written to the **response stream** | Returning a byte array would put the entire statement in the heap before the first byte reaches the client |

The test that matters walks a 520-row history — past the 500-row batch boundary — and asserts that
no reference appears twice and none is missing, which is what a broken cursor produces.

## Known gaps, by design

- Import is synchronous. A very large file would hold a request open; Phase 09 moves it onto a
  queue with a job id to poll.
- The PDF is a plain monospaced table with no branding, and the standard fonts cannot render
  non-ASCII characters, which are replaced. A real statement needs an embedded font.
- There is no CSV import, and no "dry run" that validates without writing.
