package com.example.bankcore.customer.domain;

/**
 * Sort fields a client may ask for.
 *
 * <p>An allow-list, not a free-text property name. Passing a raw string from a query parameter
 * into a sort clause lets a caller probe the internal model, trigger errors with unknown
 * properties, or sort by an unindexed column and turn a cheap query into a table scan.
 *
 * <p>Java note: the enum constant is the API vocabulary, {@link #property()} is the internal
 * entity attribute. The two are free to diverge — the API contract does not have to follow a
 * refactoring of the model.
 */
public enum CustomerSortField {

    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    FULL_NAME("fullName"),
    EMAIL("email"),
    STATUS("status");

    private final String property;

    CustomerSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
