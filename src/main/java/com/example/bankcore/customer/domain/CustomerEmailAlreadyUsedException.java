package com.example.bankcore.customer.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/**
 * Raised when an email address is already taken.
 *
 * <p>The message deliberately does not repeat the address: error responses travel into logs and
 * support tickets, and an email is personal data.
 */
public class CustomerEmailAlreadyUsedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CustomerEmailAlreadyUsedException() {
        super(ErrorCode.CUSTOMER_EMAIL_ALREADY_USED, "Email address is already registered");
    }
}
