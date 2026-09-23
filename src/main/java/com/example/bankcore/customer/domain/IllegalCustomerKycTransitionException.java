package com.example.bankcore.customer.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/** Raised when a KYC decision is not a legal move in the review lifecycle. */
public class IllegalCustomerKycTransitionException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public IllegalCustomerKycTransitionException(KycStatus from, KycStatus to) {
        super(ErrorCode.CUSTOMER_RULE_VIOLATED,
                "KYC status cannot change from %s to %s".formatted(from, to));
    }
}
