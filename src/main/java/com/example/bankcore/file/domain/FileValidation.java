package com.example.bankcore.file.domain;

import java.util.Locale;

/**
 * What an upload has to satisfy before anything is stored.
 *
 * <p>File upload is one of the classic ways into a system, so every check here has an attack
 * behind it:
 *
 * <ul>
 *   <li><b>Content type against an allow-list</b> — a deny-list only blocks what somebody thought
 *       of.</li>
 *   <li><b>Magic bytes, not the declared type</b> — the {@code Content-Type} header is written by
 *       the client. A PHP script announced as {@code image/png} is still a PHP script.</li>
 *   <li><b>Size ceiling per category</b> — an unbounded upload is a denial-of-service with no
 *       exploit needed.</li>
 *   <li><b>Generated storage key</b> — a filename like {@code ../../etc/passwd} decides where the
 *       object lands if it is used as the key.</li>
 *   <li><b>Sanitised original name</b> — it is echoed back on download, so it must not carry a
 *       path or control characters.</li>
 * </ul>
 */
public final class FileValidation {

    private static final int MAX_NAME_LENGTH = 200;

    private FileValidation() {
    }

    /**
     * @throws InvalidFileException if the upload breaks any rule
     */
    public static void validate(FileCategory category, String declaredContentType,
                                long sizeBytes, byte[] head) {
        if (sizeBytes <= 0) {
            throw new InvalidFileException("File is empty");
        }
        if (sizeBytes > category.maxSizeBytes()) {
            throw new InvalidFileException("File exceeds the %d byte limit for %s"
                    .formatted(category.maxSizeBytes(), category));
        }

        String contentType = normalizeContentType(declaredContentType);
        if (!category.allowedContentTypes().contains(contentType)) {
            throw new InvalidFileException("Content type %s is not allowed for %s"
                    .formatted(contentType, category));
        }

        if (!matchesMagicBytes(contentType, head)) {
            throw new InvalidFileException("File content does not match the declared type " + contentType);
        }
    }

    /**
     * Checks the first bytes against the type the client claims.
     *
     * <p>Only a signature check, not a full parse — it cannot prove a file is safe, but it does
     * stop the simplest and most common trick, which is renaming something executable.
     */
    public static boolean matchesMagicBytes(String contentType, byte[] head) {
        if (head == null || head.length < 4) {
            return false;
        }

        return switch (contentType) {
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "image/webp" -> head.length >= 12 && head[0] == 'R' && head[1] == 'I'
                    && head[2] == 'F' && head[3] == 'F';
            case "application/pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            // Plain text has no signature; the size and type checks are what guard it.
            case "text/plain" -> true;
            default -> false;
        };
    }

    /** Strips parameters and lower-cases, so {@code image/PNG; charset=x} matches the allow-list. */
    public static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidFileException("Content type is missing");
        }

        int parameterStart = contentType.indexOf(';');
        String base = parameterStart < 0 ? contentType : contentType.substring(0, parameterStart);
        return base.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Reduces an uploaded filename to something safe to store and echo back.
     *
     * <p>Any path component is dropped — the name is a label, never a location.
     */
    public static String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "upload";
        }

        String withoutPath = originalName.replace('\\', '/');
        withoutPath = withoutPath.substring(withoutPath.lastIndexOf('/') + 1);

        String cleaned = withoutPath.replaceAll("[^A-Za-z0-9._-]", "_").strip();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "upload";
        }

        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }
}
