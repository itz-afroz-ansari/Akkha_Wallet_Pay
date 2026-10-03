package com.wallet.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable posting in the wallet's double-entry ledger. */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_transaction", columnList = "transaction_id"),
        @Index(name = "idx_ledger_wallet_time", columnList = "wallet_id,created_at")
})
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", updatable = false)
    private Wallet wallet;

    @Column(name = "account_code", nullable = false, length = 40, updatable = false)
    private String accountCode;

    @Column(nullable = false, length = 6, updatable = false)
    private String direction;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(Transaction transaction, Wallet wallet, String accountCode,
                       String direction, BigDecimal amount, LocalDateTime createdAt) {
        this(transaction, wallet, accountCode, direction, amount, BigDecimal.ZERO, BigDecimal.ZERO, createdAt);
    }

    public LedgerEntry(Transaction transaction, Wallet wallet, String accountCode,
                       String direction, BigDecimal amount, BigDecimal balanceBefore,
                       BigDecimal balanceAfter, LocalDateTime createdAt) {
        this.transaction = transaction;
        this.wallet = wallet;
        this.accountCode = accountCode;
        this.direction = direction;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public Wallet getWallet() { return wallet; }
    public String getAccountCode() { return accountCode; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
