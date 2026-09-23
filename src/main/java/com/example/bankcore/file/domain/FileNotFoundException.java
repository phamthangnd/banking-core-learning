package com.example.bankcore.file.domain;

import com.example.bankcore.common.api.ErrorCode;
import com.example.bankcore.common.exception.BusinessException;

import java.util.UUID;

public class FileNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public FileNotFoundException(UUID id) {
        super(ErrorCode.FILE_NOT_FOUND, "File %s was not found".formatted(id));
    }
}
