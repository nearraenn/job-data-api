package com.ata.jobdata.query;

import org.springframework.http.HttpStatus;

/** An error that is the client's to fix, carried with the status and machine-readable code to return. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
