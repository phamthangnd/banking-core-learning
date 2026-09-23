package com.example.bankcore.customer.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

import java.util.UUID;

public class CustomerNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(UUID id) {
        super(ErrorCode.CUSTOMER_NOT_FOUND, "Customer %s was not found".formatted(id));
    }
}
