package com.wallet.app.service;

import com.wallet.app.entity.LedgerEntry;
import com.wallet.app.entity.SavingsGoal;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.LedgerEntryRepository;
import com.wallet.app.repository.SavingsGoalRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SavingsGoalService {
    private final SavingsGoalRepository goalRepo;
    private final UserRepository userRepo;
    private final WalletRepository walletRepo;
    private final TransactionRepository txRepo;
    private final LedgerEntryRepository ledgerRepo;

    public SavingsGoalService(SavingsGoalRepository goalRepo, UserRepository userRepo, WalletRepository walletRepo,
                              TransactionRepository txRepo, LedgerEntryRepository ledgerRepo) {
        this.goalRepo = goalRepo; this.userRepo = userRepo; this.walletRepo = walletRepo;
        this.txRepo = txRepo; this.ledgerRepo = ledgerRepo;
    }

    @Transactional(readOnly = true)
    public List<SavingsGoal> list(Long userId) { return goalRepo.findByUserIdOrderByCreatedAtDesc(userId); }

    @Transactional
    public boolean create(Long userId, String name, BigDecimal target) {
        String cleanName = name == null ? "" : name.trim();
        BigDecimal normalized = amount(target);
        if (cleanName.isBlank() || cleanName.length() > 60 || normalized == null
                || normalized.compareTo(new BigDecimal("100000000.00")) > 0) return false;
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return false;
        goalRepo.save(new SavingsGoal(user, cleanName, normalized, LocalDateTime.now()));
        return true;
    }

    @Transactional
    public boolean contribute(Long userId, Long goalId, BigDecimal amount, String operationKey) {
        BigDecimal value = amount(amount);
        if (value == null || !isUuid(operationKey)) return false;
        var previous = txRepo.findByIdempotencyKey(operationKey);
        if (previous.isPresent()) {
            Transaction tx = previous.get();
            return "SAVINGS".equals(tx.getTransactionType()) && value.compareTo(tx.getAmount()) == 0
                    && tx.getSenderPhone().equals(userRepo.findById(userId).map(User::getPhoneNumber).orElse(""))
                    && tx.getDescription().equals("Contribution to " + goalRepo.findById(goalId).map(SavingsGoal::getName).orElse(""));
        }
        SavingsGoal goal = goalRepo.findByIdForUpdate(goalId).orElse(null);
        if (goal == null || !goal.getUser().getId().equals(userId) || !"ACTIVE".equals(goal.getStatus())) return false;
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId);
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus()) || wallet.getAvailableBalance().compareTo(value) < 0) return false;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal walletBefore = wallet.getBalance();
        BigDecimal goalBefore = goal.getSavedAmount();
        Transaction tx = makeTransaction(wallet.getUser().getPhoneNumber(), "SAVINGS_GOAL", value,
                "SAVINGS", "Contribution to " + goal.getName(), operationKey, now);
        txRepo.saveAndFlush(tx);
        wallet.setBalance(walletBefore.subtract(value));
        goal.setSavedAmount(goalBefore.add(value));
        walletRepo.save(wallet);
        ledgerRepo.saveAll(List.of(
                new LedgerEntry(tx, wallet, "WALLET", "DEBIT", value, walletBefore, wallet.getBalance(), now),
                new LedgerEntry(tx, null, "SAVINGS_GOAL:" + goal.getId(), "CREDIT", value, goalBefore, goal.getSavedAmount(), now)
        ));
        return true;
    }

    @Transactional
    public boolean withdraw(Long userId, Long goalId, BigDecimal amount, String operationKey) {
        BigDecimal value = amount(amount);
        if (value == null || !isUuid(operationKey)) return false;
        var previous = txRepo.findByIdempotencyKey(operationKey);
        if (previous.isPresent()) {
            Transaction tx = previous.get();
            return "SAVINGS_WITHDRAWAL".equals(tx.getTransactionType()) && value.compareTo(tx.getAmount()) == 0
                    && tx.getReceiverPhone().equals(userRepo.findById(userId).map(User::getPhoneNumber).orElse(""))
                    && tx.getDescription().equals("Withdrawal from " + goalRepo.findById(goalId).map(SavingsGoal::getName).orElse(""));
        }
        SavingsGoal goal = goalRepo.findByIdForUpdate(goalId).orElse(null);
        if (goal == null || !goal.getUser().getId().equals(userId) || goal.getSavedAmount().compareTo(value) < 0) return false;
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId);
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus())) return false;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal walletBefore = wallet.getBalance();
        BigDecimal goalBefore = goal.getSavedAmount();
        Transaction tx = makeTransaction("SAVINGS_GOAL", wallet.getUser().getPhoneNumber(), value,
                "SAVINGS_WITHDRAWAL", "Withdrawal from " + goal.getName(), operationKey, now);
        txRepo.saveAndFlush(tx);
        wallet.setBalance(walletBefore.add(value));
        goal.setSavedAmount(goalBefore.subtract(value));
        walletRepo.save(wallet);
        ledgerRepo.saveAll(List.of(
                new LedgerEntry(tx, null, "SAVINGS_GOAL:" + goal.getId(), "DEBIT", value, goalBefore, goal.getSavedAmount(), now),
                new LedgerEntry(tx, wallet, "WALLET", "CREDIT", value, walletBefore, wallet.getBalance(), now)
        ));
        return true;
    }

    @Transactional
    public boolean close(Long userId, Long goalId) {
        SavingsGoal goal = goalRepo.findByIdForUpdate(goalId).orElse(null);
        if (goal == null || !goal.getUser().getId().equals(userId) || goal.getSavedAmount().signum() != 0) return false;
        goal.setStatus("CLOSED");
        return true;
    }

    private Transaction makeTransaction(String sender, String receiver, BigDecimal amount, String type,
                                        String description, String key, LocalDateTime now) {
        Transaction tx = new Transaction();
        tx.setSenderPhone(sender); tx.setReceiverPhone(receiver); tx.setAmount(amount);
        tx.setTimestamp(now); tx.setUpdatedAt(now); tx.setStatus("SUCCESS");
        tx.setTransactionType(type); tx.setDescription(description); tx.setIdempotencyKey(key);
        return tx;
    }

    private BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2) return null;
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { return null; }
    }

    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }
}
