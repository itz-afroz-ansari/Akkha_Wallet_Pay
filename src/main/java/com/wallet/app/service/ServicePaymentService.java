package com.wallet.app.service;

import com.wallet.app.entity.LedgerEntry;
import com.wallet.app.entity.ServicePayment;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.entity.Wallet;
import com.wallet.app.payment.PaymentGateway;
import com.wallet.app.payment.ProviderResult;
import com.wallet.app.repository.LedgerEntryRepository;
import com.wallet.app.repository.ServicePaymentRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ServicePaymentService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "MOBILE_RECHARGE", "DTH_RECHARGE", "BROADBAND", "ELECTRICITY", "WATER", "GAS", "FASTAG");
    private final UserRepository userRepo;
    private final WalletRepository walletRepo;
    private final ServicePaymentRepository paymentRepo;
    private final TransactionRepository txRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final PaymentGateway gateway;
    private final AuditLogService auditLogService;

    public ServicePaymentService(UserRepository userRepo, WalletRepository walletRepo,
                                 ServicePaymentRepository paymentRepo, TransactionRepository txRepo,
                                 LedgerEntryRepository ledgerRepo, PaymentGateway gateway, AuditLogService auditLogService) {
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.paymentRepo = paymentRepo;
        this.txRepo = txRepo;
        this.ledgerRepo = ledgerRepo;
        this.gateway = gateway;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ServicePayment pay(Long userId, String type, String provider, String accountReference,
                              BigDecimal amount, String idempotencyKey) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String normalizedProvider = provider == null ? "" : provider.trim();
        String normalizedAccount = accountReference == null ? "" : accountReference.trim();
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (!SUPPORTED_TYPES.contains(normalizedType) || normalizedProvider.isBlank()
                || normalizedProvider.length() > 60 || normalizedAccount.length() < 3
                || normalizedAccount.length() > 80 || normalizedAmount == null || !isUuid(idempotencyKey)) return null;

        ServicePayment existing = paymentRepo.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing.getUser().getId().equals(userId) ? existing : null;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return null;
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId);
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus())
                || wallet.getAvailableBalance().compareTo(normalizedAmount) < 0) return null;

        LocalDateTime now = LocalDateTime.now();
        ServicePayment payment = paymentRepo.saveAndFlush(new ServicePayment(user, normalizedType,
                normalizedProvider, normalizedAccount, normalizedAmount, idempotencyKey, now));
        ProviderResult result = gateway.process(normalizedType, normalizedAccount, normalizedAmount);

        BigDecimal balanceBefore = wallet.getBalance();
        Transaction tx = new Transaction();
        tx.setSenderPhone(user.getPhoneNumber());
        tx.setReceiverPhone("DEMO_" + normalizedType);
        tx.setAmount(normalizedAmount);
        tx.setTimestamp(now);
        tx.setUpdatedAt(now);
        tx.setStatus(result.status());
        tx.setTransactionType(normalizedType);
        tx.setDescription(normalizedProvider + " payment");
        tx.setReferenceId(result.reference());
        tx.setIdempotencyKey(idempotencyKey);

        if ("SUCCESS".equals(result.status())) {
            wallet.setBalance(balanceBefore.subtract(normalizedAmount));
            walletRepo.save(wallet);
            user.setRewardPoints(user.getRewardPoints() + Math.max(1, normalizedAmount.longValue() / 100));
            userRepo.save(user);
            txRepo.saveAndFlush(tx);
            ledgerRepo.saveAll(List.of(
                    new LedgerEntry(tx, wallet, "WALLET", "DEBIT", normalizedAmount,
                            balanceBefore, wallet.getBalance(), now),
                    new LedgerEntry(tx, null, "SERVICE_CLEARING", "CREDIT", normalizedAmount,
                            BigDecimal.ZERO, BigDecimal.ZERO, now)
            ));
        } else {
            tx.setFailureReason(result.message());
            txRepo.saveAndFlush(tx);
        }

        payment.setStatus(result.status());
        payment.setMessage(result.message());
        payment.setProviderReference(result.reference());
        payment.setTransaction(tx);
        auditLogService.record(userId, "SERVICE_PAYMENT", "SERVICE_PAYMENT", payment.getId().toString(),
                normalizedType + " " + result.status());
        return payment;
    }

    @Transactional
    public ServicePayment addDemoFunds(Long userId, BigDecimal amount, String paymentMethod,
                                       String cardNumber, String expiry, String cvv, String idempotencyKey) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String normalizedMethod = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase();
        if (normalizedAmount == null || !Set.of("DEBIT_CARD", "CREDIT_CARD").contains(normalizedMethod)
                || !validCardDetails(cardNumber, expiry, cvv) || !isUuid(idempotencyKey)) return null;
        ServicePayment existing = paymentRepo.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing.getUser().getId().equals(userId)
                && "ADD_MONEY".equals(existing.getServiceType()) ? existing : null;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return null;
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId);
        if (wallet == null || !"ACTIVE".equals(wallet.getStatus())) return null;

        LocalDateTime now = LocalDateTime.now();
        String methodLabel = "CREDIT_CARD".equals(normalizedMethod) ? "Credit card" : "Debit card";
        String demoCard = "DEMO_" + normalizedMethod;
        ServicePayment payment = paymentRepo.saveAndFlush(new ServicePayment(user, "ADD_MONEY",
                "Mock " + methodLabel, demoCard, normalizedAmount, idempotencyKey, now));
        ProviderResult result = gateway.process("ADD_MONEY_" + normalizedMethod, demoCard, normalizedAmount);
        Transaction tx = new Transaction();
        tx.setSenderPhone("DEMO_GATEWAY"); tx.setReceiverPhone(user.getPhoneNumber());
        tx.setAmount(normalizedAmount); tx.setTimestamp(now); tx.setUpdatedAt(now);
        tx.setStatus("SUCCESS".equals(result.status()) ? "BANK_DEPOSIT" : result.status());
        tx.setTransactionType("ADD_MONEY"); tx.setDescription("Simulated " + methodLabel.toLowerCase() + " wallet top-up");
        tx.setReferenceId(result.reference()); tx.setIdempotencyKey(idempotencyKey);

        if ("SUCCESS".equals(result.status())) {
            BigDecimal balanceBefore = wallet.getBalance();
            wallet.setBalance(balanceBefore.add(normalizedAmount));
            walletRepo.save(wallet);
            user.setRewardPoints(user.getRewardPoints() + Math.max(1, normalizedAmount.longValue() / 100));
            userRepo.save(user);
            txRepo.saveAndFlush(tx);
            ledgerRepo.saveAll(List.of(
                    new LedgerEntry(tx, null, "DEMO_CLEARING", "DEBIT", normalizedAmount,
                            BigDecimal.ZERO, BigDecimal.ZERO, now),
                    new LedgerEntry(tx, wallet, "WALLET", "CREDIT", normalizedAmount,
                            balanceBefore, wallet.getBalance(), now)
            ));
        } else {
            tx.setFailureReason(result.message());
            txRepo.saveAndFlush(tx);
        }
        payment.setStatus(result.status()); payment.setMessage(result.message());
        payment.setProviderReference(result.reference()); payment.setTransaction(tx);
        auditLogService.record(userId, "DEMO_TOPUP", "SERVICE_PAYMENT", payment.getId().toString(), result.status());
        return payment;
    }

    @Transactional(readOnly = true)
    public List<ServicePayment> history(Long userId) {
        return paymentRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) return null;
        try {
            BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
            return value.compareTo(new BigDecimal("1000000.00")) <= 0 ? value : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }

    /** Validate demo checkout field formats only; never persist or log card data. */
    private boolean validCardDetails(String cardNumber, String expiry, String cvv) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]{12,19}") || cvv == null || !cvv.matches("[0-9]{3,4}")) return false;
        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) return false;
        int month = Integer.parseInt(expiry.substring(0, 2));
        int year = 2000 + Integer.parseInt(expiry.substring(3, 5));
        return !java.time.YearMonth.of(year, month).isBefore(java.time.YearMonth.now());
    }
}
