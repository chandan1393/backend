package com.assignease.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler — converts validation errors to clean JSON responses.
 * Prevents stack traces leaking to the frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean validation failures (@Valid @NotBlank @Email etc.) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", errors.get(0)); // first error for simple display
        body.put("errors", errors);          // all errors for detailed display
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** File too large */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("message", "File too large. Maximum allowed size is 50MB."));
    }

    /** Catch-all — never expose stack traces to client */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        // Log it server-side but return generic message
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", "An unexpected error occurred. Please try again."));
    }
}
