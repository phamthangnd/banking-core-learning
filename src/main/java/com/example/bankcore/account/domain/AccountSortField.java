package com.example.bankcore.account.domain;

/** Sort fields a client may ask for — an allow-list, never a raw property name from the query string. */
public enum AccountSortField {

    OPENED_AT("openedAt"),
    UPDATED_AT("updatedAt"),
    ACCOUNT_NUMBER("accountNumber"),
    BALANCE("balance"),
    STATUS("status");

    private final String property;

    AccountSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
