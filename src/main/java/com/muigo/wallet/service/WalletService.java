package com.muigo.wallet.service;

import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.exceptions.WalletExceptions.*;
import com.muigo.wallet.models.Transaction;
import com.muigo.wallet.models.Transaction.TransactionType;
import com.muigo.wallet.models.Wallet;
import com.muigo.wallet.repositories.TransactionRepository;
import com.muigo.wallet.repositories.WalletRepository;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    private DistributionSummary transactionAmountSummary;
    private Timer transferTimer;
    private Timer depositTimer;
    private Timer withdrawTimer;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void initMetrics() {

        // Total system balance — uses SQL SUM, not findAll()
        Gauge.builder("wallet.total_balance", walletRepository,
                        repo -> repo.sumAllBalances().doubleValue())
                .description("Sum of balances across all wallets")
                .register(meterRegistry);

        // Transaction amount distribution
        transactionAmountSummary = DistributionSummary.builder("wallet.transaction.amount")
                .description("Distribution of transaction amounts")
                .baseUnit("currency_units")
                .publishPercentileHistogram()
                .register(meterRegistry);

        // Operation timers
        transferTimer = Timer.builder("wallet.operation.duration")
                .tag("operation", "transfer")
                .description("Time taken to process a wallet transfer")
                .publishPercentileHistogram()
                .register(meterRegistry);

        depositTimer = Timer.builder("wallet.operation.duration")
                .tag("operation", "deposit")
                .description("Time taken to process a deposit")
                .publishPercentileHistogram()
                .register(meterRegistry);

        withdrawTimer = Timer.builder("wallet.operation.duration")
                .tag("operation", "withdraw")
                .description("Time taken to process a withdrawal")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    // ── Operations ───────────────────────────────────────────────────────────

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        Wallet wallet = Wallet.builder()
                .ownerName(request.getOwnerName())
                .balance(BigDecimal.ZERO)
                .build();
        Wallet saved = walletRepository.save(wallet);
        log.info("Created wallet {} for owner '{}'", saved.getId(), saved.getOwnerName());

        meterRegistry.counter("wallet.created.total").increment();

        return toResponse(saved);
    }

    @Transactional
    public WalletResponse deposit(DepositRequest request) {
        return depositTimer.record(() -> doDeposit(request));
    }

    private WalletResponse doDeposit(DepositRequest request) {
        validateAmount(request.getAmount());

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> {
                    recordError("wallet_not_found", "deposit");
                    return new WalletNotFoundException(request.getWalletId());
                });

        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        Wallet saved = walletRepository.save(wallet);

        recordTransaction(Transaction.builder()
                .walletId(saved.getId())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .balanceAfter(saved.getBalance())
                .description(request.getDescription())
                .build());

        recordSuccess("deposit");
        transactionAmountSummary.record(request.getAmount().doubleValue());

        log.info("Deposited {} to wallet {}. New balance: {}",
                request.getAmount(), saved.getId(), saved.getBalance());
        return toResponse(saved);
    }

    @Transactional
    public WalletResponse withdraw(WithdrawRequest request) {
        return withdrawTimer.record(() -> doWithdraw(request));
    }

    private WalletResponse doWithdraw(WithdrawRequest request) {
        validateAmount(request.getAmount());

        Wallet wallet = walletRepository.findById(request.getWalletId())
                .orElseThrow(() -> {
                    recordError("wallet_not_found", "withdrawal");
                    return new WalletNotFoundException(request.getWalletId());
                });

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            recordInsufficientFunds("withdrawal");
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

        recordSuccess("withdrawal");
        transactionAmountSummary.record(request.getAmount().doubleValue());

        log.info("Withdrew {} from wallet {}. New balance: {}",
                request.getAmount(), saved.getId(), saved.getBalance());
        return toResponse(saved);
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        return transferTimer.record(() -> doTransfer(request));
    }

    private TransferResponse doTransfer(TransferRequest request) {
        validateAmount(request.getAmount());

        if (request.getFromWalletId().equals(request.getToWalletId())) {
            recordError("self_transfer", "transfer");
            throw new InvalidAmountException("Cannot transfer to the same wallet");
        }

        String firstId = request.getFromWalletId().compareTo(request.getToWalletId()) < 0
                ? request.getFromWalletId() : request.getToWalletId();
        String secondId = firstId.equals(request.getFromWalletId())
                ? request.getToWalletId() : request.getFromWalletId();

        Wallet first = walletRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> {
                    recordError("wallet_not_found", "transfer");
                    return new WalletNotFoundException(firstId);
                });
        Wallet second = walletRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> {
                    recordError("wallet_not_found", "transfer");
                    return new WalletNotFoundException(secondId);
                });

        Wallet from = first.getId().equals(request.getFromWalletId()) ? first : second;
        Wallet to   = first.getId().equals(request.getToWalletId())   ? first : second;

        if (from.getBalance().compareTo(request.getAmount()) < 0) {
            recordInsufficientFunds("transfer");
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

        recordSuccess("transfer");
        transactionAmountSummary.record(request.getAmount().doubleValue());

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

    // ── Metric Helpers ───────────────────────────────────────────────────────

    private void recordSuccess(String type) {
        Counter.builder("wallet.transactions.total")
                .tag("type", type)
                .tag("status", "success")
                .register(meterRegistry)
                .increment();
    }

    private void recordInsufficientFunds(String type) {
        Counter.builder("wallet.transactions.total")
                .tag("type", type)
                .tag("status", "insufficient_funds")
                .register(meterRegistry)
                .increment();

        Counter.builder("wallet.errors.total")
                .tag("type", "insufficient_funds")
                .tag("operation", type)
                .register(meterRegistry)
                .increment();
    }

    private void recordError(String errorType, String operation) {
        Counter.builder("wallet.errors.total")
                .tag("type", errorType)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    // ── Domain Helpers ───────────────────────────────────────────────────────

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