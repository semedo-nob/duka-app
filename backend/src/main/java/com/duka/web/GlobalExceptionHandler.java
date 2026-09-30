package com.duka.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "Invalid request";
        }
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("error", "You don't have permission to do that"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
        String text = ex.getMostSpecificCause().getMessage() == null ? "" : ex.getMostSpecificCause().getMessage();
        if (text.contains("negative_stock_not_allowed")) {
            return ResponseEntity.status(409).body(Map.of("error", "Not enough stock"));
        }
        if (text.contains("idempotency")) {
            return ResponseEntity.status(409).body(Map.of("error", "This sale was already submitted"));
        }
        if (text.contains("barcode") || text.contains("sku")) {
            return ResponseEntity.status(409).body(Map.of("error", "A product with that SKU or barcode already exists"));
        }
        log.warn("Data conflict", ex);
        return ResponseEntity.status(409).body(Map.of("error", "That change conflicts with existing data"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.status(500).body(Map.of("error", "Something went wrong"));
    }
}
