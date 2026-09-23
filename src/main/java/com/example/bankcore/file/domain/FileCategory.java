package com.example.bankcore.file.domain;

import java.util.Set;

/**
 * What a stored file is for.
 *
 * <p>The category decides the rules: what may be uploaded, how large it may be and who may read
 * it. A KYC document is not an avatar, and treating them the same means either avatars accept
 * PDFs or identity documents are limited to a profile picture's size.
 */
public enum FileCategory {

    /** Profile picture. Images only, small. */
    AVATAR(Set.of("image/jpeg", "image/png", "image/webp"), 2 * 1024 * 1024L),

    /** Identity or address evidence. Images or PDF, larger. */
    KYC_DOCUMENT(Set.of("image/jpeg", "image/png", "application/pdf"), 10 * 1024 * 1024L),

    /** Generated account statement. */
    STATEMENT(Set.of("application/pdf"), 20 * 1024 * 1024L),

    /** Anything else a back-office user attaches. */
    OTHER(Set.of("image/jpeg", "image/png", "application/pdf", "text/plain"), 10 * 1024 * 1024L);

    private final Set<String> allowedContentTypes;
    private final long maxSizeBytes;

    FileCategory(Set<String> allowedContentTypes, long maxSizeBytes) {
        this.allowedContentTypes = allowedContentTypes;
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * An allow-list, never a deny-list. A deny-list is a list of the attacks somebody thought of;
     * everything not on it is accepted, including the next file type nobody considered.
     */
    public Set<String> allowedContentTypes() {
        return allowedContentTypes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }
}
