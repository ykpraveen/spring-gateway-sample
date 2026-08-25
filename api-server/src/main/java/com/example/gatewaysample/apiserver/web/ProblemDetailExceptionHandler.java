package com.example.gatewaysample.apiserver.web;

import com.example.gatewaysample.apiserver.downstream.DownstreamClientErrorException;
import com.example.gatewaysample.apiserver.downstream.DownstreamUnavailableException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    @ExceptionHandler(DownstreamClientErrorException.class)
    public ResponseEntity<Map<String, Object>> handleClientError(DownstreamClientErrorException ex) {
        return ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ex.body());
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUnavailable(DownstreamUnavailableException ex) {
        String code = ex.service().toUpperCase(Locale.ROOT).replace('-', '_') + "_UNAVAILABLE";
        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), code);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleValidation(WebExchangeBindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ResponseEntity<ProblemDetail> response =
                problemResponse(HttpStatus.BAD_REQUEST, "Request payload failed validation", "VALIDATION_ERROR");
        response.getBody().setProperty("fieldErrors", fieldErrors);
        return response;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR");
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
