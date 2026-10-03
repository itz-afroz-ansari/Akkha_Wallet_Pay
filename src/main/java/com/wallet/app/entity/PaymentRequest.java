package com.wallet.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_requests", indexes = {
        @Index(name = "idx_request_payer_status", columnList = "payer_id,status,expires_at"),
        @Index(name = "idx_request_requester_time", columnList = "requester_id,created_at")
})
public class PaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false, updatable = false)
    private User payer;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 100, updatable = false)
    private String note;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;

    protected PaymentRequest() {}

    public PaymentRequest(User requester, User payer, BigDecimal amount, String note,
                          LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.requester = requester;
        this.payer = payer;
        this.amount = amount;
        this.note = note;
        this.status = "PENDING";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getRequester() { return requester; }
    public User getPayer() { return payer; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
}
