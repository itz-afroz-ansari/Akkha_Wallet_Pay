package com.wallet.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_goals", indexes = @Index(name = "idx_savings_goal_user", columnList = "user_id,status"))
public class SavingsGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 60, updatable = false)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal targetAmount;

    @Column(name = "saved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SavingsGoal() {}

    public SavingsGoal(User user, String name, BigDecimal targetAmount, LocalDateTime createdAt) {
        this.user = user;
        this.name = name;
        this.targetAmount = targetAmount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getSavedAmount() { return savedAmount; }
    public void setSavedAmount(BigDecimal savedAmount) { this.savedAmount = savedAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
