package com.example.bankcore.user.domain;

/** Lifecycle of a user account. */
public enum UserStatus {

    /** Can authenticate. */
    ACTIVE,

    /** Temporarily locked after repeated failed logins; unlocks itself when the lock expires. */
    LOCKED,

    /** Deactivated by an administrator. Only an administrator can reverse it. */
    DISABLED
}
