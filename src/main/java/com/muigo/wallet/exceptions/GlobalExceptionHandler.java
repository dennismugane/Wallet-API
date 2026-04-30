package com.muigo.wallet.exceptions;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception handler using RFC 7807 Problem Details.
 * Every error response has the same structure — essential for API consumers
 * and for writing reliable tests.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletExceptions.WalletNotFoundException.class)
    public ProblemDetail handleNotFound(WalletExceptions.WalletNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "wallet-not-found", ex.getMessage());
    }

    @ExceptionHandler(WalletExceptions.InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(WalletExceptions.InsufficientFundsException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds", ex.getMessage());
    }

    @ExceptionHandler(WalletExceptions.InvalidAmountException.class)
    public ProblemDetail handleInvalidAmount(WalletExceptions.InvalidAmountException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-amount", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrency(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict detected", ex);
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "Wallet was modified concurrently. Please retry the operation.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "validation-error", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred. Please try again.");
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://api.muigo.com/errors/" + type));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
