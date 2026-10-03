package com.wallet.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_payments", indexes = {
        @Index(name = "idx_service_payment_user_time", columnList = "user_id,created_at")
})
public class ServicePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;

    @Column(name = "service_type", nullable = false, length = 24, updatable = false)
    private String serviceType;

    @Column(name = "provider_name", nullable = false, length = 60, updatable = false)
    private String providerName;

    @Column(name = "account_reference", nullable = false, length = 80, updatable = false)
    private String accountReference;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(length = 240)
    private String message;

    @Column(name = "provider_reference", length = 40)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ServicePayment() {}

    public ServicePayment(User user, String serviceType, String providerName, String accountReference,
                          BigDecimal amount, String idempotencyKey, LocalDateTime createdAt) {
        this.user = user;
        this.serviceType = serviceType;
        this.providerName = providerName;
        this.accountReference = accountReference;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
    public String getServiceType() { return serviceType; }
    public String getProviderName() { return providerName; }
    public String getAccountReference() { return accountReference; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
