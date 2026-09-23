package com.example.bankcore.common.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One page of results plus the numbers a client needs to navigate.
 *
 * @param content       elements of this page, never null
 * @param page          zero-based index of this page
 * @param size          requested page size
 * @param totalElements total number of matching elements across all pages
 * @param <T>           element type
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
    }

    public static <T> PageResult<T> empty(PageRequest request) {
        return new PageResult<>(List.of(), request.page(), request.size(), 0);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    /** Converts the elements while keeping the paging numbers — domain model to DTO, typically. */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements);
    }
}
