package com.padmakar.trustflow.web;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = CaseApiController.class)
public class ApiErrorAdvice {

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<?> notFound(RecordNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> stale(ConflictException e) {
        return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    )
    public ResponseEntity<?> invalidParameter() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid query parameter"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
            .getFieldErrors()
            .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
            Map.of("message", "Check the highlighted fields.", "fields", fields)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request values."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(
            Map.of("message", "You do not have permission for this action.")
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("message", "Record not found."));
    }

    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        DataIntegrityViolationException.class,
    })
    public ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
            Map.of("message", "This record changed. Refresh before trying again.")
        );
    }
}
