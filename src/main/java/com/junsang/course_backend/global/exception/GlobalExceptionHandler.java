package com.junsang.course_backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 비즈니스 예외 ─────────────────────────────────────────────────────
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("[BusinessException] code={} message={}", errorCode.name(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    // ── 요청 검증 예외 ────────────────────────────────────────────────────
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        log.warn("[InvalidRequest] type={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("[IllegalArgument] message={}", exception.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    // ── HTTP 예외 ─────────────────────────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.warn("[MethodNotAllowed] method={} supported={}", exception.getMethod(), exception.getSupportedHttpMethods());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ErrorResponse.from(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // ── 예상하지 못한 예외 ────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("[UnexpectedException]", exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
