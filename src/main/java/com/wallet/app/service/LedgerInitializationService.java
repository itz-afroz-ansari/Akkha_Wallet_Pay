package com.wallet.app.service;

import com.wallet.app.entity.LedgerEntry;
import com.wallet.app.entity.Transaction;
import com.wallet.app.entity.Wallet;
import com.wallet.app.repository.LedgerEntryRepository;
import com.wallet.app.repository.TransactionRepository;
import com.wallet.app.repository.UserRepository;
import com.wallet.app.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Carries forward balances created before the ledger existed, without changing them. */
@Service
public class LedgerInitializationService {
    private final WalletRepository walletRepo;
    private final TransactionRepository txRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final UserRepository userRepo;

    public LedgerInitializationService(WalletRepository walletRepo, TransactionRepository txRepo,
                                       LedgerEntryRepository ledgerRepo, UserRepository userRepo) {
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
        this.ledgerRepo = ledgerRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void carryForwardLegacyBalances() {
        for (Wallet wallet : walletRepo.findAll()) {
            boolean walletChanged = false;
            if (wallet.getWalletNumber() == null || wallet.getWalletNumber().isBlank()) {
                wallet.setWalletNumber("WLT" + UUID.randomUUID().toString().replace("-", "").substring(0, 13).toUpperCase());
                walletChanged = true;
            }
            if (wallet.getCurrency() == null || wallet.getCurrency().isBlank()) {
                wallet.setCurrency("INR");
                walletChanged = true;
            }
            if (wallet.getStatus() == null || wallet.getStatus().isBlank()) {
                wallet.setStatus("ACTIVE");
                walletChanged = true;
            }
            if (wallet.getReservedBalance().signum() == 0) {
                wallet.setReservedBalance(java.math.BigDecimal.ZERO);
                walletChanged = true;
            }
            if (walletChanged) walletRepo.save(wallet);

            if (wallet.getUser() != null) {
                var user = wallet.getUser();
                boolean userChanged = false;
                if (user.getRole() == null || user.getRole().isBlank()) { user.setRole("CUSTOMER"); userChanged = true; }
                if (user.getKycStatus() == null || user.getKycStatus().isBlank()) { user.setKycStatus("NOT_STARTED"); userChanged = true; }
                if (user.getAccountStatus() == null || user.getAccountStatus().isBlank()) { user.setAccountStatus("ACTIVE"); userChanged = true; }
                if (userChanged) userRepo.save(user);
            }
            if (wallet.getBalance() == null || wallet.getBalance().signum() <= 0
                    || ledgerRepo.existsByWalletId(wallet.getId())) continue;

            String phone = wallet.getUser().getPhoneNumber();
            LocalDateTime now = LocalDateTime.now();
            Transaction opening = new Transaction();
            opening.setSenderPhone("SYSTEM");
            opening.setReceiverPhone(phone);
            opening.setAmount(wallet.getBalance());
            opening.setTimestamp(now);
            opening.setUpdatedAt(now);
            opening.setStatus("OPENING_BALANCE");
            opening.setTransactionType("OPENING_BALANCE");
            opening.setDescription("Balance carried forward when ledger was enabled");
            opening.setIdempotencyKey("opening:" + wallet.getId());
            txRepo.saveAndFlush(opening);
            ledgerRepo.saveAll(List.of(
                    new LedgerEntry(opening, null, "LEGACY_BALANCE", "DEBIT", wallet.getBalance(), now),
                    new LedgerEntry(opening, wallet, "WALLET", "CREDIT", wallet.getBalance(), now)
            ));
        }
    }
}
