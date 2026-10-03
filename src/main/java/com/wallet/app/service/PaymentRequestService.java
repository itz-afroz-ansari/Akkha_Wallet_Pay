package com.wallet.app.service;

import com.wallet.app.entity.PaymentRequest;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.User;
import com.wallet.app.repository.PaymentRequestRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentRequestService {
    private final PaymentRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final TransactionRepository txRepo;
    private final WalletService walletService;

    public PaymentRequestService(PaymentRequestRepository requestRepo, UserRepository userRepo,
                                 TransactionRepository txRepo, WalletService walletService) {
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
        this.txRepo = txRepo;
        this.walletService = walletService;
    }

    @Transactional
    public boolean createRequest(Long requesterId, String payerPhone, BigDecimal amount, String note) {
        BigDecimal normalized = normalizeAmount(amount);
        String normalizedPhone = payerPhone == null ? "" : payerPhone.replaceAll("[\\s()-]", "");
        if (normalized == null || normalizedPhone.isBlank()) return false;

        User requester = userRepo.findById(requesterId).orElse(null);
        User payer = userRepo.findByPhoneNumber(normalizedPhone).orElse(null);
        if (requester == null || payer == null || requester.getId().equals(payer.getId())) return false;

        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 100) return false;
        LocalDateTime now = LocalDateTime.now();
        requestRepo.save(new PaymentRequest(requester, payer, normalized, safeNote, now, now.plusDays(7)));
        return true;
    }

    @Transactional
    public boolean splitRequest(Long requesterId, String participants, BigDecimal totalAmount, String note) {
        BigDecimal total = normalizeAmount(totalAmount);
        if (total == null || participants == null) return false;
        String[] submittedPhones = participants.split(",");
        if (submittedPhones.length < 2 || submittedPhones.length > 10) return false;
        User requester = userRepo.findById(requesterId).orElse(null);
        if (requester == null) return false;
        String safeNote = note == null ? "Split bill" : note.trim();
        if (safeNote.isBlank()) safeNote = "Split bill";
        if (safeNote.length() > 100) return false;

        List<User> payers = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String phone : submittedPhones) {
            String normalized = phone.replaceAll("[\\s()-]", "");
            User payer = userRepo.findByPhoneNumber(normalized).orElse(null);
            if (payer == null || payer.getId().equals(requesterId) || !unique.add(normalized)) return false;
            payers.add(payer);
        }

        long cents;
        try { cents = total.movePointRight(2).longValueExact(); }
        catch (ArithmeticException ex) { return false; }
        long each = cents / payers.size();
        long remainder = cents % payers.size();
        if (each == 0) return false;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < payers.size(); i++) {
            long shareCents = each + (i < remainder ? 1 : 0);
            requestRepo.save(new PaymentRequest(requester, payers.get(i), BigDecimal.valueOf(shareCents, 2),
                    safeNote, now, now.plusDays(7)));
        }
        return true;
    }

    @Transactional
    public boolean pay(Long requestId, Long payerId) {
        PaymentRequest request = requestRepo.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.getPayer().getId().equals(payerId)) return false;
        if ("PAID".equals(request.getStatus())) return true;
        if (!"PENDING".equals(request.getStatus())) return false;

        LocalDateTime now = LocalDateTime.now();
        if (!request.getExpiresAt().isAfter(now)) {
            request.setStatus("EXPIRED");
            return false;
        }

        String operationKey = UUID.nameUUIDFromBytes(("payment-request:" + request.getId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        boolean paid = walletService.sendMoney(request.getPayer().getPhoneNumber(),
                request.getRequester().getPhoneNumber(), request.getAmount(), operationKey);
        if (!paid) return false;

        Transaction tx = txRepo.findByIdempotencyKey(operationKey).orElse(null);
        request.setTransaction(tx);
        request.setStatus("PAID");
        return true;
    }

    @Transactional
    public boolean decline(Long requestId, Long payerId) {
        PaymentRequest request = requestRepo.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.getPayer().getId().equals(payerId)
                || !"PENDING".equals(request.getStatus())) return false;
        request.setStatus(request.getExpiresAt().isAfter(LocalDateTime.now()) ? "DECLINED" : "EXPIRED");
        return true;
    }

    @Transactional
    public boolean cancel(Long requestId, Long requesterId) {
        PaymentRequest request = requestRepo.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.getRequester().getId().equals(requesterId)
                || !"PENDING".equals(request.getStatus())) return false;
        request.setStatus(request.getExpiresAt().isAfter(LocalDateTime.now()) ? "CANCELLED" : "EXPIRED");
        return true;
    }

    @Transactional
    public List<PaymentRequest> inbox(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        requestRepo.expireForPayer(userId, now);
        requestRepo.expireForRequester(userId, now);
        return requestRepo.findByPayerIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public List<PaymentRequest> sent(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        requestRepo.expireForPayer(userId, now);
        requestRepo.expireForRequester(userId, now);
        return requestRepo.findByRequesterIdOrderByCreatedAtDesc(userId);
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
}
