package com.muigo.wallet.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class WalletExceptions {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException(String walletId) {
            super("Wallet not found: " + walletId);
        }
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String walletId) {
            super("Insufficient funds in wallet: " + walletId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidAmountException extends RuntimeException {
        public InvalidAmountException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConcurrentModificationException extends RuntimeException {
        public ConcurrentModificationException() {
            super("Wallet was modified concurrently. Please retry the operation.");
        }
    }
}
