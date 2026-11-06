package com.noticiasplaneta.shared.adapter.in.web;

import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce errores de negocio a HTTP con un código estable. Nunca devuelve stacktraces.
 */
@RestControllerAdvice
class DomainExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handle(DomainException e) {
        return ResponseEntity.status(statusFor(e.code()))
                .body(new ApiError(e.code(), e.getMessage()));
    }

    private HttpStatus statusFor(String code) {
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        return switch (code) {
            case "FORBIDDEN", "OUT_OF_SCOPE", "AGENT_CANNOT_PUBLISH" -> HttpStatus.FORBIDDEN;
            case "ILLEGAL_TRANSITION", "ALREADY_RUNNING" -> HttpStatus.CONFLICT;
            case "SOURCE_DISABLED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
