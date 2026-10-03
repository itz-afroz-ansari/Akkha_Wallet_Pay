package com.wallet.app.service;

import com.wallet.app.entity.LedgerEntry;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.LedgerEntryRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private final UserRepository userRepo;
    private final WalletRepository walletRepo;
    private final TransactionRepository txRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final AuditLogService auditLogService;

    public WalletService(UserRepository userRepo, WalletRepository walletRepo,
                         TransactionRepository txRepo, LedgerEntryRepository ledgerRepo, AuditLogService auditLogService) {
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
        this.ledgerRepo = ledgerRepo;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public boolean sendMoney(String senderPhone, String receiverPhone, BigDecimal amount, String idempotencyKey) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount == null || senderPhone == null || receiverPhone == null
                || senderPhone.equals(receiverPhone) || !isValidIdempotencyKey(idempotencyKey)) return false;

        User sender = userRepo.findByPhoneNumber(senderPhone).orElse(null);
        User receiver = userRepo.findByPhoneNumber(receiverPhone).orElse(null);
        if (sender == null || receiver == null || sender.getId().equals(receiver.getId())) return false;

        // Always lock wallets in the same order to reduce deadlocks on simultaneous transfers.
        Long firstId = Math.min(sender.getId(), receiver.getId());
        Long secondId = Math.max(sender.getId(), receiver.getId());
        Wallet firstWallet = walletRepo.findByUserIdForUpdate(firstId);
        Wallet secondWallet = walletRepo.findByUserIdForUpdate(secondId);
        if (firstWallet == null || secondWallet == null) return false;

        var existing = txRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transaction prior = existing.get();
            return senderPhone.equals(prior.getSenderPhone())
                    && receiverPhone.equals(prior.getReceiverPhone())
                    && normalizedAmount.compareTo(prior.getAmount()) == 0
                    && "SUCCESS".equals(prior.getStatus());
        }

        Wallet senderWallet = sender.getId().equals(firstId) ? firstWallet : secondWallet;
        Wallet receiverWallet = receiver.getId().equals(firstId) ? firstWallet : secondWallet;
        if (!"ACTIVE".equals(senderWallet.getStatus()) || !"ACTIVE".equals(receiverWallet.getStatus())
                || senderWallet.getAvailableBalance().compareTo(normalizedAmount) < 0) return false;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal senderBefore = senderWallet.getBalance();
        BigDecimal receiverBefore = receiverWallet.getBalance();
        Transaction tx = new Transaction();
        tx.setSenderPhone(senderPhone);
        tx.setReceiverPhone(receiverPhone);
        tx.setAmount(normalizedAmount);
        tx.setTimestamp(now);
        tx.setUpdatedAt(now);
        tx.setStatus("SUCCESS");
        tx.setTransactionType("SEND_MONEY");
        tx.setDescription("Wallet transfer");
        tx.setIdempotencyKey(idempotencyKey);
        txRepo.saveAndFlush(tx);

        senderWallet.setBalance(senderWallet.getBalance().subtract(normalizedAmount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(normalizedAmount));
        walletRepo.saveAll(List.of(senderWallet, receiverWallet));

        ledgerRepo.saveAll(List.of(
                new LedgerEntry(tx, senderWallet, "WALLET", "DEBIT", normalizedAmount,
                        senderBefore, senderWallet.getBalance(), now),
                new LedgerEntry(tx, receiverWallet, "WALLET", "CREDIT", normalizedAmount,
                        receiverBefore, receiverWallet.getBalance(), now)
        ));
        auditLogService.record(sender.getId(), "MONEY_TRANSFER", "TRANSACTION", tx.getId().toString(),
                "Wallet transfer completed");
        return true;
    }

    @Transactional
    public boolean addMoneyToWallet(String phoneNumber, BigDecimal amount, String paymentId) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount == null || phoneNumber == null || paymentId == null || paymentId.isBlank()) return false;

        User user = userRepo.findByPhoneNumber(phoneNumber).orElse(null);
        if (user == null) return false;
        Wallet wallet = walletRepo.findByUserIdForUpdate(user.getId());
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus()) || txRepo.existsByReferenceId(paymentId)) return false;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal balanceBefore = wallet.getBalance();
        Transaction tx = new Transaction();
        tx.setSenderPhone("PAYMENT_PROVIDER");
        tx.setReceiverPhone(phoneNumber);
        tx.setAmount(normalizedAmount);
        tx.setTimestamp(now);
        tx.setUpdatedAt(now);
        tx.setStatus("BANK_DEPOSIT");
        tx.setTransactionType("ADD_MONEY");
        tx.setDescription("Verified payment provider top-up");
        tx.setReferenceId(paymentId);
        tx.setIdempotencyKey("provider:" + paymentId);
        txRepo.saveAndFlush(tx);

        wallet.setBalance(wallet.getBalance().add(normalizedAmount));
        walletRepo.save(wallet);
        ledgerRepo.saveAll(List.of(
                new LedgerEntry(tx, null, "EXTERNAL_CLEARING", "DEBIT", normalizedAmount, now),
                new LedgerEntry(tx, wallet, "WALLET", "CREDIT", normalizedAmount,
                        balanceBefore, wallet.getBalance(), now)
        ));
        return true;
    }

    @Transactional(readOnly = true)
    public List<Transaction> fetchTransactionHistory(String phone) {
        return txRepo.findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc(phone, phone);
    }

    @Transactional
    public boolean setWalletStatus(Long userId, String action) {
        if (!"FREEZE".equals(action) && !"UNFREEZE".equals(action)) return false;
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId);
        if (wallet == null) return false;
        wallet.setStatus("FREEZE".equals(action) ? "FROZEN" : "ACTIVE");
        walletRepo.save(wallet);
        auditLogService.record(userId, "WALLET_STATUS_CHANGE", "WALLET", wallet.getId().toString(), wallet.getStatus());
        return true;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) return null;
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            return normalized.precision() <= 19 ? normalized : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private boolean isValidIdempotencyKey(String key) {
        if (key == null || key.length() != 36) return false;
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
