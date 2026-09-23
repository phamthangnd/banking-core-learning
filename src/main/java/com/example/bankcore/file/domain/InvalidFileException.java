package com.example.bankcore.file.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

/** An upload that fails validation. The message is safe to show the uploader. */
public class InvalidFileException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public InvalidFileException(String message) {
        super(ErrorCode.INVALID_FILE, message);
    }
}
