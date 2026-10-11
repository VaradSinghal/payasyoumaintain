package com.paymu.claims.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.paymu.claims.model.ClaimedCause;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = "Request body is malformed or invalid.";
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof InvalidFormatException)
                && !(cause instanceof JsonParseException)
                && !(cause instanceof MismatchedInputException)) {
            if (cause.getCause() == null || cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }

        if (cause instanceof InvalidFormatException ife) {
            String fieldName = extractFieldName(ife.getPath());
            Class<?> targetType = ife.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                List<String> allowedValues = new ArrayList<>();
                for (Object constant : targetType.getEnumConstants()) {
                    if (constant instanceof ClaimedCause cc) {
                        allowedValues.add(cc.getValue());
                    } else {
                        try {
                            var method = constant.getClass().getMethod("getValue");
                            allowedValues.add(String.valueOf(method.invoke(constant)));
                        } catch (Exception ignored) {
                            allowedValues.add(constant.toString().toLowerCase());
                        }
                    }
                }
                message = "Invalid value for field '" + fieldName + "'. Allowed values: " + allowedValues;
            } else {
                message = "Invalid value for field '" + fieldName + "': expected "
                        + (targetType != null ? targetType.getSimpleName() : "valid format");
            }
        } else if (cause instanceof MismatchedInputException mie) {
            String fieldName = extractFieldName(mie.getPath());
            Class<?> targetType = mie.getTargetType();
            message = "Invalid value for field '" + fieldName + "': expected "
                    + (targetType != null ? targetType.getSimpleName() : "valid format");
        } else if (cause instanceof JsonParseException) {
            message = "Malformed JSON request body.";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INVALID_REQUEST");
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    private String extractFieldName(List<JsonMappingException.Reference> path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            String name = path.get(i).getFieldName();
            if (name != null) {
                return name;
            }
        }
        return "unknown";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String value = ex.getValue() != null ? ex.getValue().toString() : "null";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INVALID_REQUEST");
        body.put("message", "Invalid value '" + value + "' for parameter '" + name + "'.");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach(e -> {
            String field = e instanceof FieldError fe ? fe.getField() : e.getObjectName();
            details.add(field + ": " + e.getDefaultMessage());
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INVALID_REQUEST");
        body.put("message", "Validation failed: " + String.join(", ", details));
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "NOT_FOUND");
        body.put("message", "The requested resource could not be found.");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INVALID_REQUEST");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Invalid argument");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "An unexpected error occurred.");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.internalServerError().body(body);
    }
}
