package com.muigo.wallet.service;

import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.exceptions.WalletExceptions.*;
import com.muigo.wallet.models.Transaction;
import com.muigo.wallet.models.Transaction.TransactionType;
import com.muigo.wallet.models.Wallet;
import com.muigo.wallet.repositories.TransactionRepository;
import com.muigo.wallet.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core wallet business logic.
 *
 * Key decisions:
 * - @Transactional on all write methods: if any step fails, the entire
 *   operation rolls back. A transfer that debits the sender but fails
 *   to credit the receiver will be fully rolled back.
 * - Pessimistic locking on transfer: wallets are locked in consistent
 *   ID order (lower ID first) to prevent deadlocks when two concurrent
 *   transfers involve the same two wallets in opposite directions.
 * - Every financial event writes a Transaction record — immutable audit log.
 * - BigDecimal for all monetary arithmetic — exact precision, no float errors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        Wallet wallet = Wallet.builder()
                .ownerName(request.getOwnerName())
                .balance(BigDecimal.ZERO)
                .build();
        Wallet saved = walletRepository.save(wallet);
        log.info("Created wallet {} for owner '{}'", saved.getId(), saved.getOwnerName());
        return toResponse(saved);
    }

    @Transactional
    public WalletResponse deposit(DepositRequest request) {
        validateAmount(request.getAmount());

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        Wallet saved = walletRepository.save(wallet);

        recordTransaction(Transaction.builder()
                .walletId(saved.getId())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .balanceAfter(saved.getBalance())
                .description(request.getDescription())
                .build());

        log.info("Deposited {} to wallet {}. New balance: {}",
                request.getAmount(), saved.getId(), saved.getBalance());
        return toResponse(saved);
    }

    @Transactional
    public WalletResponse withdraw(WithdrawRequest request) {
        validateAmount(request.getAmount());

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(request.getWalletId());
        }

        wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));
        Wallet saved = walletRepository.save(wallet);

        recordTransaction(Transaction.builder()
                .walletId(saved.getId())
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .balanceAfter(saved.getBalance())
                .description(request.getDescription())
                .build());

        log.info("Withdrew {} from wallet {}. New balance: {}",
                request.getAmount(), saved.getId(), saved.getBalance());
        return toResponse(saved);
    }

    /**
     * Atomic transfer between two wallets.
     *
     * Locking strategy: acquire pessimistic write locks on both wallets,
     * always in lexicographic ID order. This prevents deadlock: if thread A
     * locks wallet-1 then wallet-2, and thread B also locks wallet-1 first
     * (because IDs are sorted), they never hold locks in opposite orders.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        validateAmount(request.getAmount());

        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidAmountException("Cannot transfer to the same wallet");
        }

        // Lock wallets in consistent order to prevent deadlock
        String firstId = request.getFromWalletId().compareTo(request.getToWalletId()) < 0
                ? request.getFromWalletId() : request.getToWalletId();
        String secondId = firstId.equals(request.getFromWalletId())
                ? request.getToWalletId() : request.getFromWalletId();

        Wallet first = walletRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new WalletNotFoundException(firstId));
        Wallet second = walletRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new WalletNotFoundException(secondId));

        Wallet from = first.getId().equals(request.getFromWalletId()) ? first : second;
        Wallet to   = first.getId().equals(request.getToWalletId())   ? first : second;

        if (from.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(from.getId());
        }

        from.setBalance(from.getBalance().subtract(request.getAmount()));
        to.setBalance(to.getBalance().add(request.getAmount()));

        walletRepository.save(from);
        walletRepository.save(to);

        String referenceId = UUID.randomUUID().toString();

        recordTransaction(Transaction.builder()
                .walletId(from.getId()).referenceId(referenceId)
                .type(TransactionType.TRANSFER_DEBIT)
                .amount(request.getAmount()).balanceAfter(from.getBalance())
                .description(request.getDescription()).build());

        recordTransaction(Transaction.builder()
                .walletId(to.getId()).referenceId(referenceId)
                .type(TransactionType.TRANSFER_CREDIT)
                .amount(request.getAmount()).balanceAfter(to.getBalance())
                .description(request.getDescription()).build());

        log.info("Transferred {} from wallet {} to wallet {}. Ref: {}",
                request.getAmount(), from.getId(), to.getId(), referenceId);

        return TransferResponse.builder()
                .referenceId(referenceId)
                .amount(request.getAmount())
                .fromWallet(toResponse(from))
                .toWallet(toResponse(to))
                .build();
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String walletId) {
        return walletRepository.findById(walletId)
                .map(this::toResponse)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(String walletId, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(walletId, pageable)
                .map(this::toTransactionResponse);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }

    private void recordTransaction(Transaction transaction) {
        transactionRepository.save(transaction);
    }

    private WalletResponse toResponse(Wallet w) {
        return WalletResponse.builder()
                .id(w.getId())
                .ownerName(w.getOwnerName())
                .balance(w.getBalance())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private TransactionResponse toTransactionResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .walletId(t.getWalletId())
                .referenceId(t.getReferenceId())
                .type(t.getType())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
