package com.example.ticket.gate.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        ErrorResponse response = ErrorResponse.of(code, ex.getMessage());
        return Mono.just(ResponseEntity.status(code.getHttpStatus()).body(response));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_ERROR", "Internal server error", System.currentTimeMillis());
        return Mono.just(ResponseEntity.status(500).body(response));
    }
}
