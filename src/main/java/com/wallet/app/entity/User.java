package com.wallet.app.entity;



import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
 // Add this inside User.java
    private String pin;

    // Add the Getter and Setter
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
    
    @Column(nullable = false)
    private String name;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(unique = true, nullable = false)
    private String phoneNumber;
    
    
    private String currentOtp;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Wallet wallet;

    
    private boolean isVerified = false; // Maps directly to is_verified automatically

    @Column(nullable = false, length = 12)
    private String role = "CUSTOMER";

    @Column(name = "kyc_status", nullable = false, length = 16)
    private String kycStatus = "NOT_STARTED";

    @Column(name = "reward_points", nullable = false)
    private long rewardPoints = 0;

    @Column(name = "account_status", nullable = false, length = 12)
    private String accountStatus = "ACTIVE";

 // Add these getter/setter methods
 public boolean isVerified() { return isVerified; }
 public void setVerified(boolean verified) { isVerified = verified; }
 public String getRole() { return role; }
 public void setRole(String role) { this.role = role; }
 public String getKycStatus() { return kycStatus; }
 public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }
 public long getRewardPoints() { return rewardPoints; }
 public void setRewardPoints(long rewardPoints) { this.rewardPoints = rewardPoints; }
 public String getAccountStatus() { return accountStatus; }
 public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getCurrentOtp() { return currentOtp; }
    public void setCurrentOtp(String currentOtp) { this.currentOtp = currentOtp; }
    public Wallet getWallet() { return wallet; }
    public void setWallet(Wallet wallet) { this.wallet = wallet; }
}
