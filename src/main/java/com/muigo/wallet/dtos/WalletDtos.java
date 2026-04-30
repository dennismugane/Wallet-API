package com.muigo.wallet.dtos;

import com.muigo.wallet.models.Transaction;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * All request/response DTOs live here.
 *
 * Key decisions:
 * - Request DTOs use Bean Validation annotations — the controller never
 *   receives invalid data.
 * - Response DTOs never expose entity internals (no JPA version field, etc.).
 * - BigDecimal for all monetary amounts.
 */
public class WalletDtos {

    // ── Requests ────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateWalletRequest {
        @NotBlank(message = "Owner name is required")
        @Size(min = 2, max = 100, message = "Owner name must be between 2 and 100 characters")
        private String ownerName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositRequest {
        @NotBlank(message = "Wallet ID is required")
        private String walletId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
        @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
        private BigDecimal amount;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawRequest {
        @NotBlank(message = "Wallet ID is required")
        private String walletId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Withdrawal amount must be at least 0.01")
        @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
        private BigDecimal amount;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRequest {
        @NotBlank(message = "Source wallet ID is required")
        private String fromWalletId;

        @NotBlank(message = "Destination wallet ID is required")
        private String toWalletId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
        @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
        private BigDecimal amount;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank @Email(message = "Valid email required")
        private String email;

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank
        private String fullName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    // ── Responses ───────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletResponse {
        private String id;
        private String ownerName;
        private BigDecimal balance;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private String id;
        private String walletId;
        private String referenceId;
        private Transaction.TransactionType type;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private String description;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferResponse {
        private String referenceId;
        private BigDecimal amount;
        private WalletResponse fromWallet;
        private WalletResponse toWallet;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String tokenType;
        private String userId;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder().success(true).data(data).build();
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder().success(true).message(message).data(data).build();
        }
    }
}
