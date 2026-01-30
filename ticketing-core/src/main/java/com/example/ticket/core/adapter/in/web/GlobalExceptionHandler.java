package com.example.ticket.core.adapter.in.web;

import com.example.ticket.common.error.BusinessException;
import com.example.ticket.common.error.ErrorCode;
import com.example.ticket.common.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String msg = ex.getMessage();

        ErrorCode code;
        if (msg != null && msg.contains("holds_schedule_id_seat_id_key")) {
            code = ErrorCode.SEAT_ALREADY_HELD;
        } else if (msg != null && msg.contains("holds_schedule_id_client_id_key")) {
            code = ErrorCode.ALREADY_HOLDING;
        } else {
            code = ErrorCode.INVALID_REQUEST;
        }

        return Mono.just(ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse response = new ErrorResponse("INTERNAL_ERROR", "Internal server error", System.currentTimeMillis());
        return Mono.just(ResponseEntity.status(500).body(response));
    }
}
