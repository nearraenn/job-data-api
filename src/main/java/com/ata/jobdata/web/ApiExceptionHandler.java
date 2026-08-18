package com.ata.jobdata.web;

import com.ata.jobdata.query.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every client mistake comes back in the same shape, with a code a client can branch on. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(Detail error) {
        public record Detail(String code, String message) {}

        static ApiError of(String code, String message) {
            return new ApiError(new Detail(code, message));
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }
}
