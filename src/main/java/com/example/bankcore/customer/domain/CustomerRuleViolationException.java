package com.example.bankcore.customer.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/** A well-formed request that a customer domain rule rejects. */
public class CustomerRuleViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CustomerRuleViolationException(String message) {
        super(ErrorCode.CUSTOMER_RULE_VIOLATED, message);
    }
}
