package com.routeassign.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralised exception handler for all REST controllers.
 * Converts every known exception type into a consistent {@link ErrorResponse} payload.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid status transition: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 422 Unprocessable Entity ──────────────────────────────────────────────

    @ExceptionHandler(InvalidRuleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRule(
            InvalidRuleException ex, HttpServletRequest request) {
        log.warn("Invalid rule value: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientCapacityException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientCapacity(
            InsufficientCapacityException ex, HttpServletRequest request) {
        log.warn("Insufficient capacity: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(NoEligiblePartnerException.class)
    public ResponseEntity<ErrorResponse> handleNoEligiblePartner(
            NoEligiblePartnerException ex, HttpServletRequest request) {
        log.warn("No eligible partner: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // ── 400 Validation Errors (Bean Validation) ───────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        BindingResult bindingResult = ex.getBindingResult();

        List<ErrorResponse.FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .fieldErrors(fieldErrors)
                .build();

        log.warn("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 500 Internal Server Error (catch-all) ─────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
