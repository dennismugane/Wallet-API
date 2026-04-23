package com.muigo.wallet.models;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable ledger record — every financial event is recorded here.
 * Transactions are never updated or deleted; they are append-only.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transaction_wallet_id", columnList = "walletId"),
        @Index(name = "idx_transaction_created_at", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String walletId;

    /** For transfers, this links the two legs of the same transaction. */
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Balance of the wallet after this transaction was applied. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT
    }
}
