package com.example.bankcore.user.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

public class UserNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND, "User was not found");
    }
}
