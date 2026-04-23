package com.muigo.wallet.models;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wallet entity.
 *
 * Key decisions:
 * - BigDecimal for balance — never use double/float for money (precision loss).
 * - @Version for optimistic locking — prevents lost-update race conditions on
 *   concurrent deposits/withdrawals without full table-level locking.
 * - @EntityListeners for automatic createdAt/updatedAt population.
 */
@Entity
@Table(name = "wallets")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Optimistic locking version field.
     * If two threads read the same wallet and both try to save,
     * the second save throws OptimisticLockException — preventing
     * a race condition that could allow double-spending.
     */
    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
