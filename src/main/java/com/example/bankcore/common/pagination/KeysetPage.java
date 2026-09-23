package com.example.bankcore.common.pagination;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A page addressed by "what came last" rather than by an offset.
 *
 * <p>Why this exists alongside {@link PageResult}: {@code OFFSET} makes the database produce and
 * discard every skipped row, so page 2000 costs two thousand pages of work. The measurement in
 * Phase 02 showed it — an offset of 40 000 needed an external merge sort of 5 976 kB, against
 * 4.3 ms for the equivalent keyset query.
 *
 * <p>The trade-off is honest: keyset paging cannot jump to an arbitrary page number and cannot
 * report a total count cheaply. It is right for endless scrolling and for exports; offset paging
 * stays right for a back-office table with numbered pages.
 *
 * @param content elements of this page
 * @param nextCursor cursor to pass back for the next page, or empty at the end
 * @param size   requested page size
 */
public record KeysetPage<T>(List<T> content, String nextCursor, int size) {

    public KeysetPage {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
    }

    public static <T> KeysetPage<T> of(List<T> content, String nextCursor, int size) {
        return new KeysetPage<>(content, nextCursor, size);
    }

    public Optional<String> nextCursorOrEmpty() {
        return Optional.ofNullable(nextCursor);
    }

    public boolean hasNext() {
        return nextCursor != null;
    }
}
