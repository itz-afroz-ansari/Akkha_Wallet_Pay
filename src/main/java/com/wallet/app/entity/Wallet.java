package com.wallet.app.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "wallet_number", unique = true, length = 20)
    private String walletNumber;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";

    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getWalletNumber() { return walletNumber; }
    public void setWalletNumber(String walletNumber) { this.walletNumber = walletNumber; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getReservedBalance() { return reservedBalance == null ? BigDecimal.ZERO : reservedBalance; }
    public void setReservedBalance(BigDecimal reservedBalance) { this.reservedBalance = reservedBalance; }
    public BigDecimal getAvailableBalance() {
        return (balance == null ? BigDecimal.ZERO : balance).subtract(getReservedBalance());
    }

    @PrePersist
    private void assignWalletNumber() {
        if (walletNumber == null || walletNumber.isBlank()) {
            walletNumber = "WLT" + UUID.randomUUID().toString().replace("-", "").substring(0, 13).toUpperCase();
        }
    }
}
