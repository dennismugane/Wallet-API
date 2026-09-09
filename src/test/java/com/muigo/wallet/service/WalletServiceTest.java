package com.muigo.wallet.service;

import com.muigo.wallet.dtos.WalletDtos.*;
import com.muigo.wallet.exceptions.WalletExceptions.*;
import com.muigo.wallet.models.Transaction;
import com.muigo.wallet.models.Wallet;
import com.muigo.wallet.repositories.TransactionRepository;
import com.muigo.wallet.repositories.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;

    MeterRegistry meterRegistry;
    WalletService walletService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        walletService = new WalletService(
                walletRepository,
                transactionRepository,
                meterRegistry
        );

        walletService.initMetrics();

        wallet = Wallet.builder()
                .id("wallet-1")
                .ownerName("Test User")
                .balance(new BigDecimal("1000.00"))
                .version(0L)
                .build();
    }

    // ── createWallet ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createWallet")
    class CreateWallet {

        @Test
        @DisplayName("creates wallet with zero balance")
        void createsWalletWithZeroBalance() {
            when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

            WalletResponse response = walletService.createWallet(
                    new CreateWalletRequest("Test User"));

            assertThat(response.getOwnerName()).isEqualTo("Test User");
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("increments wallet.created.total counter")
        void incrementsCreatedCounter() {
            when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

            walletService.createWallet(new CreateWalletRequest("Test User"));

            double count = meterRegistry.counter("wallet.created.total").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    // ── deposit ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deposit")
    class Deposit {

        @Test
        @DisplayName("increases balance by the deposit amount")
        void increasesBalance() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            WalletResponse response = walletService.deposit(
                    new DepositRequest("wallet-1", new BigDecimal("500.00"), "Test deposit"));

            assertThat(response.getBalance()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("records a DEPOSIT transaction")
        void recordsDepositTransaction() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.deposit(new DepositRequest("wallet-1", new BigDecimal("100.00"), null));

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getType())
                    .isEqualTo(Transaction.TransactionType.DEPOSIT);
        }

        @Test
        @DisplayName("increments success counter on deposit")
        void incrementsSuccessCounter() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.deposit(new DepositRequest("wallet-1", new BigDecimal("100.00"), null));

            double count = meterRegistry.counter("wallet.transactions.total",
                    "type", "deposit", "status", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records deposit timer")
        void recordsDepositTimer() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.deposit(new DepositRequest("wallet-1", new BigDecimal("100.00"), null));

            Timer timer = meterRegistry.timer("wallet.operation.duration", "operation", "deposit");
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("throws WalletNotFoundException when wallet does not exist")
        void throwsWhenWalletNotFound() {
            when(walletRepository.findById("bad-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                walletService.deposit(
                        new DepositRequest("bad-id", new BigDecimal("100"), null)))
                    .isInstanceOf(WalletNotFoundException.class);
        }

        @Test
        @DisplayName("increments error counter when wallet not found")
        void incrementsErrorCounterOnNotFound() {
            when(walletRepository.findById("bad-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                walletService.deposit(
                        new DepositRequest("bad-id", new BigDecimal("100"), null)))
                    .isInstanceOf(WalletNotFoundException.class);

            double count = meterRegistry.counter("wallet.errors.total",
                    "type", "wallet_not_found", "operation", "deposit").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("throws InvalidAmountException for zero amount")
        void throwsOnZeroAmount() {
            assertThatThrownBy(() ->
                walletService.deposit(
                        new DepositRequest("wallet-1", BigDecimal.ZERO, null)))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("throws InvalidAmountException for negative amount")
        void throwsOnNegativeAmount() {
            assertThatThrownBy(() ->
                walletService.deposit(
                        new DepositRequest("wallet-1", new BigDecimal("-50"), null)))
                    .isInstanceOf(InvalidAmountException.class);
        }
    }

    // ── withdraw ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("decreases balance by the withdrawal amount")
        void decreasesBalance() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            WalletResponse response = walletService.withdraw(
                    new WithdrawRequest("wallet-1", new BigDecimal("200.00"), null));

            assertThat(response.getBalance()).isEqualByComparingTo("800.00");
        }

        @Test
        @DisplayName("throws InsufficientFundsException when balance is too low")
        void throwsOnInsufficientFunds() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() ->
                walletService.withdraw(
                        new WithdrawRequest("wallet-1", new BigDecimal("9999.00"), null)))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("increments insufficient_funds counter on withdrawal")
        void incrementsInsufficientFundsCounter() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() ->
                walletService.withdraw(
                        new WithdrawRequest("wallet-1", new BigDecimal("9999.00"), null)))
                    .isInstanceOf(InsufficientFundsException.class);

            double txCount = meterRegistry.counter("wallet.transactions.total",
                    "type", "withdrawal", "status", "insufficient_funds").count();
            double errCount = meterRegistry.counter("wallet.errors.total",
                    "type", "insufficient_funds", "operation", "withdrawal").count();

            assertThat(txCount).isEqualTo(1.0);
            assertThat(errCount).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments success counter on withdrawal")
        void incrementsSuccessCounter() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.withdraw(
                    new WithdrawRequest("wallet-1", new BigDecimal("100.00"), null));

            double count = meterRegistry.counter("wallet.transactions.total",
                    "type", "withdrawal", "status", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records withdraw timer")
        void recordsWithdrawTimer() {
            when(walletRepository.findById("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.withdraw(
                    new WithdrawRequest("wallet-1", new BigDecimal("100.00"), null));

            Timer timer = meterRegistry.timer("wallet.operation.duration", "operation", "withdraw");
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    // ── transfer ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("transfer")
    class Transfer {

        private Wallet receiver;

        @BeforeEach
        void setUp() {
            receiver = Wallet.builder()
                    .id("wallet-2")
                    .ownerName("Receiver")
                    .balance(new BigDecimal("500.00"))
                    .version(0L)
                    .build();
        }

        @Test
        @DisplayName("debits sender and credits receiver atomically")
        void transfersCorrectly() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            TransferResponse response = walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("300.00"), null));

            assertThat(response.getFromWallet().getBalance()).isEqualByComparingTo("700.00");
            assertThat(response.getToWallet().getBalance()).isEqualByComparingTo("800.00");
        }

        @Test
        @DisplayName("records two transaction legs with the same referenceId")
        void recordsTwoLegsWithSameReference() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("100.00"), null));

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository, times(2)).save(captor.capture());

            String ref = captor.getAllValues().get(0).getReferenceId();
            assertThat(ref).isNotNull();
            assertThat(captor.getAllValues().get(1).getReferenceId()).isEqualTo(ref);
        }

        @Test
        @DisplayName("throws InvalidAmountException when transferring to same wallet")
        void rejectsSelfTransfer() {
            assertThatThrownBy(() ->
                walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-1", new BigDecimal("100"), null)))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        @DisplayName("throws InsufficientFundsException when sender has insufficient balance")
        void throwsOnInsufficientFunds() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));

            assertThatThrownBy(() ->
                walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("9999.00"), null)))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("increments transfer success counter")
        void incrementsTransferSuccessCounter() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("100.00"), null));

            double count = meterRegistry.counter("wallet.transactions.total",
                    "type", "transfer", "status", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments insufficient_funds error counters on transfer")
        void incrementsInsufficientFundsOnTransfer() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));

            assertThatThrownBy(() ->
                walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("9999.00"), null)))
                    .isInstanceOf(InsufficientFundsException.class);

            double errCount = meterRegistry.counter("wallet.errors.total",
                    "type", "insufficient_funds", "operation", "transfer").count();
            assertThat(errCount).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments self_transfer error counter")
        void incrementsSelfTransferErrorCounter() {
            assertThatThrownBy(() ->
                walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-1", new BigDecimal("100"), null)))
                    .isInstanceOf(InvalidAmountException.class);

            double count = meterRegistry.counter("wallet.errors.total",
                    "type", "self_transfer", "operation", "transfer").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records transfer timer")
        void recordsTransferTimer() {
            when(walletRepository.findByIdWithLock("wallet-1")).thenReturn(Optional.of(wallet));
            when(walletRepository.findByIdWithLock("wallet-2")).thenReturn(Optional.of(receiver));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            walletService.transfer(
                    new TransferRequest("wallet-1", "wallet-2", new BigDecimal("100.00"), null));

            Timer timer = meterRegistry.timer("wallet.operation.duration", "operation", "transfer");
            assertThat(timer.count()).isEqualTo(1);
        }
    }
}