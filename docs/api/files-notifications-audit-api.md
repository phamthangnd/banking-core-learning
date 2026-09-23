# Files, Notifications and Audit API

## Files — `/api/v1/files`

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/files` (multipart) | `file:write` | Upload; `category` and optional `customerId` as parameters |
| GET | `/files/{id}` | `file:read` | Metadata |
| GET | `/files/{id}/content` | `file:read` | Download |
| GET | `/files?customerId=` | `file:read` | A customer's files |
| DELETE | `/files/{id}` | `file:write` | Soft-delete the row, remove the object |

Bytes live in object storage (MinIO locally, any S3-compatible bucket in production); the database
holds the metadata, which is what makes access checkable.

### Validation — every rule has an attack behind it

| Rule | What it stops |
|---|---|
| Content type on the **category's allow-list** | A deny-list only blocks what somebody thought of |
| **Magic bytes** checked against the declared type | `Content-Type` is written by the client; a script announced as `image/png` is still a script |
| **Size ceiling per category** | An unbounded upload is a denial of service with no exploit needed |
| **Generated storage key** | `../../etc/passwd` as a filename would otherwise decide where the object lands |
| **Sanitised original name** | It is echoed back on download, so it must carry no path or control characters |

| Category | Types | Max size |
|---|---|---|
| `AVATAR` | JPEG, PNG, WebP | 2 MB |
| `KYC_DOCUMENT` | JPEG, PNG, PDF | 10 MB |
| `STATEMENT` | PDF | 20 MB |
| `OTHER` | JPEG, PNG, PDF, text | 10 MB |

Downloads are always `Content-Disposition: attachment` with `X-Content-Type-Options: nosniff`.
Serving user-uploaded content inline lets an uploaded HTML or SVG run script in the application's
origin — stored cross-site scripting.

Deletion is soft: the row stays as evidence that the file existed and who uploaded it, the object
is removed so personal data is not kept longer than needed.

## Notifications — `/api/v1/notifications`

| Method | Path | Purpose |
|---|---|---|
| GET | `/notifications?page=&size=&unreadOnly=` | The caller's inbox, newest first |
| GET | `/notifications/unread-count` | Badge count |
| POST | `/notifications/{id}/read` | Mark read (idempotent) |
| POST | `/notifications/{id}/unread` | Mark unread |
| POST | `/notifications/read-all` | Mark everything read |

Every endpoint works on the authenticated user, taken from the token. There is no "notifications
of user X" endpoint. Touching someone else's notification answers **404**, not 403: confirming
that it exists would already leak something.

A notification is a **pointer** — type, title, body and a `resourceType`/`resourceId` — never a
copy of the data. Copying an amount or a balance into it would duplicate sensitive data into a
store with weaker access rules, and it would go stale.

### Email

`EmailSender` is a port. `SmtpEmailSender` is active only when
`bankcore.notification.email.enabled=true`; otherwise `LoggingEmailSender` records that an email
would have been sent. Delivery failures are logged, never propagated: failing a transfer because
its confirmation email bounced would be worse than the missing email.

## Audit — `/api/v1/audit/events`

Requires `audit:read`, which only administrators hold — reading the trail is itself a privileged
action.

| Parameter | Meaning |
|---|---|
| `actorId`, `resourceType`, `resourceId` | Filters |
| `from`, `to` | Half-open time range |
| `page`, `size` | Paging; the trail is always sorted newest first |

Each entry records **who** (actor id and the username at the time), **what** (action), **which
record** (resource type and id), **when**, the **outcome** (`SUCCESS`, `FAILURE`, `DENIED`) and
the request's `traceId`, so an entry leads straight to its log lines.

`DENIED` is kept separate from `FAILURE` on purpose: a permission refusal is a security signal —
a known account reaching outside its role — while a failure is usually a rule saying no.

Audited today: registration, login success and failure, password change, deposits, withdrawals,
transfers, reversals and rejected movements, file uploads, downloads, deletions and rejections.

### What never appears in an audit entry

Passwords, tokens, balances, amounts, full account numbers or file content. The trail says *that*
something happened and to which record — not what the record contains (CLAUDE.md section 4).

The table is append-only, enforced by database triggers: `UPDATE` and `DELETE` are refused with
*"audit events are append-only"*. An audit trail that can be edited proves nothing, and the first
thing an intruder edits is the record of the intrusion.

Writing an entry never fails the operation it audits; a write failure is logged loudly instead.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `bankcore.file.endpoint` | `http://localhost:9000` | S3-compatible endpoint |
| `bankcore.file.bucket` | `bankcore` | Bucket for every object |
| `bankcore.file.access-key` / `secret-key` | from the environment | Storage credentials |
| `bankcore.notification.email.enabled` | `false` | Turns on SMTP delivery |

## Known gaps, by design

- No virus scanning. Magic bytes stop renamed executables, not malware; a real deployment needs a
  scanner in front of the bucket.
- Downloads stream from storage on every request — no signed URLs and no CDN.
- Notifications are created in-process. Phase 09 moves them onto a queue so a slow delivery
  cannot hold up the transaction that caused it.
- The audit trail has no retention policy or export yet.
